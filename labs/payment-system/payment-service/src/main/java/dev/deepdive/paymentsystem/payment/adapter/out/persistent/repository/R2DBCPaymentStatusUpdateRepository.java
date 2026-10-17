package dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository;

import dev.deepdive.paymentsystem.payment.adapter.out.stream.PaymentEventMessagePublisher;
import dev.deepdive.paymentsystem.payment.adapter.out.stream.util.PartitionKeyUtil;
import dev.deepdive.paymentsystem.payment.application.exception.PaymentAlreadyProcessedException;
import dev.deepdive.paymentsystem.payment.application.exception.PaymentNotFoundException;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import dev.deepdive.paymentsystem.payment.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.payment.domain.PaymentExtraDetails;
import dev.deepdive.paymentsystem.payment.domain.PaymentOrder;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 결제 주문 상태 전이를 기록한다. 모든 전이는 한 트랜잭션 안에서
 * <ol>
 *     <li>해당 결제의 주문 행을 {@code SELECT ... FOR UPDATE} 로 잠그고</li>
 *     <li>이전 상태가 허용된 상태인지 확인한 뒤</li>
 *     <li>이력 INSERT 와 조건부 UPDATE({@code WHERE payment_order_status IN (...)})를 한다.</li>
 * </ol>
 * 같은 결제에 대한 동시 요청은 행 락에서 직렬화되고, 뒤에 온 요청은 앞 요청이 커밋한 상태를 보고 판단한다.
 */
@Repository
public class R2DBCPaymentStatusUpdateRepository implements PaymentStatusUpdateRepository {

    private static final int HISTORY_REASON_MAX_LENGTH = 255;

    // 승인 요청(confirm)이 EXECUTING 으로 선점할 수 있는 이전 상태.
    private static final Set<PaymentStatus> CONFIRMABLE = EnumSet.of(PaymentStatus.NOT_STARTED, PaymentStatus.UNKNOWN);
    // 최종 상태(SUCCESS/FAILURE/UNKNOWN)를 기록할 수 있는 이전 상태.
    private static final Set<PaymentStatus> FINALIZABLE = EnumSet.of(PaymentStatus.EXECUTING, PaymentStatus.UNKNOWN);

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;
    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentEventMessagePublisher paymentEventMessagePublisher;
    private final PartitionKeyUtil partitionKeyUtil;

    public R2DBCPaymentStatusUpdateRepository(
            DatabaseClient databaseClient,
            TransactionalOperator transactionalOperator,
            PaymentOutboxRepository paymentOutboxRepository,
            PaymentEventMessagePublisher paymentEventMessagePublisher,
            PartitionKeyUtil partitionKeyUtil
    ) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
        this.paymentOutboxRepository = paymentOutboxRepository;
        this.paymentEventMessagePublisher = paymentEventMessagePublisher;
        this.partitionKeyUtil = partitionKeyUtil;
    }

    @Override
    public Mono<Boolean> updatePaymentStatusToExecuting(String orderId, String paymentKey) {
        return lockPaymentOrders(orderId)
                .flatMap(paymentOrders -> {
                    if (paymentOrders.isEmpty()) {
                        return Mono.error(new PaymentNotFoundException(orderId));
                    }
                    // 이미 끝났거나(SUCCESS/FAILURE) 다른 요청이 승인 중(EXECUTING)이면 PSP 를 다시 부르지 않는다.
                    // EXECUTING 이 오래 멈춰 있으면 사용자 재요청이 아니라 복구 스케줄러가 이어받는다.
                    PaymentStatus blocking = firstStatusOutside(paymentOrders, CONFIRMABLE);
                    if (blocking != null) {
                        return Mono.error(alreadyProcessed(blocking));
                    }
                    return insertPaymentHistory(paymentOrders, PaymentStatus.EXECUTING, "PAYMENT_CONFIRMATION_START")
                            .then(updatePaymentOrderStatus(orderId, PaymentStatus.EXECUTING, CONFIRMABLE))
                            .then(updatePaymentKey(orderId, paymentKey));
                })
                .as(transactionalOperator::transactional)
                .thenReturn(true);
    }

    @Override
    public Mono<Boolean> updatePaymentStatusToExecutingForRecovery(String orderId) {
        return lockPaymentOrders(orderId)
                .flatMap(paymentOrders -> {
                    boolean claimable = !paymentOrders.isEmpty() && paymentOrders.stream().allMatch(it ->
                            it.failedCount() < it.threshold()
                                    && (it.status() == PaymentStatus.UNKNOWN
                                    || (it.status() == PaymentStatus.EXECUTING && it.stale())));
                    if (!claimable) {
                        return Mono.just(false);
                    }
                    return insertPaymentHistory(paymentOrders, PaymentStatus.EXECUTING, "PAYMENT_RECOVERY_START")
                            .then(updatePaymentOrderStatus(orderId, PaymentStatus.EXECUTING, FINALIZABLE))
                            .thenReturn(true);
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Boolean> updatePaymentStatus(PaymentStatusUpdateCommand command) {
        return switch (command.status()) {
            case SUCCESS -> updatePaymentStatusToSuccess(command);
            case FAILURE -> updatePaymentStatusToFailure(command);
            case UNKNOWN -> updatePaymentStatusToUnknown(command);
            default -> throw new IllegalStateException(
                    "결제 상태 (status: " + command.status() + ") 는 올바르지 않은 결제 상태입니다.");
        };
    }

    private Mono<Boolean> updatePaymentStatusToSuccess(PaymentStatusUpdateCommand command) {
        return lockFinalizablePaymentOrders(command.orderId())
                .flatMap(paymentOrders -> insertPaymentHistory(paymentOrders, command.status(), "PAYMENT_CONFIRMATION_DONE")
                        .then(updatePaymentOrderStatus(command.orderId(), command.status(), FINALIZABLE))
                        .then(updatePaymentEventExtraDetails(command))
                        .then(Mono.fromCallable(() -> createSuccessEventMessage(command, paymentOrders))))
                .flatMap(paymentOutboxRepository::insertOutbox)
                .flatMap(paymentEventMessagePublisher::publishEvent)
                .as(transactionalOperator::transactional)
                .thenReturn(true);
    }

    private Mono<Boolean> updatePaymentStatusToFailure(PaymentStatusUpdateCommand command) {
        return lockFinalizablePaymentOrders(command.orderId())
                .flatMap(paymentOrders -> insertPaymentHistory(paymentOrders, command.status(), String.valueOf(command.failure()))
                        .then(updatePaymentOrderStatus(command.orderId(), command.status(), FINALIZABLE)))
                .as(transactionalOperator::transactional)
                .thenReturn(true);
    }

    private Mono<Boolean> updatePaymentStatusToUnknown(PaymentStatusUpdateCommand command) {
        return lockFinalizablePaymentOrders(command.orderId())
                .flatMap(paymentOrders -> insertPaymentHistory(paymentOrders, command.status(), String.valueOf(command.failure()))
                        .then(updatePaymentOrderStatus(command.orderId(), command.status(), FINALIZABLE))
                        .then(incrementPaymentOrderFailedCount(command)))
                .as(transactionalOperator::transactional)
                .thenReturn(true);
    }

    /**
     * 최종 상태 기록 전 확인: 이미 SUCCESS/FAILURE 인 결제를 덮어쓰지 않는다(예: 복구가 먼저 SUCCESS 를 썼는데 늦게 온 응답이 FAILURE 를 쓰는 경우).
     */
    private Mono<List<LockedPaymentOrder>> lockFinalizablePaymentOrders(String orderId) {
        return lockPaymentOrders(orderId)
                .flatMap(paymentOrders -> {
                    if (paymentOrders.isEmpty()) {
                        return Mono.error(new PaymentNotFoundException(orderId));
                    }
                    PaymentStatus blocking = firstStatusOutside(paymentOrders, FINALIZABLE);
                    if (blocking == PaymentStatus.SUCCESS || blocking == PaymentStatus.FAILURE) {
                        return Mono.error(alreadyProcessed(blocking));
                    }
                    if (blocking != null) {
                        return Mono.error(new IllegalStateException(
                                "결제 (orderId: " + orderId + ") 는 " + blocking + " 상태라 최종 상태로 바꿀 수 없습니다."));
                    }
                    return Mono.just(paymentOrders);
                });
    }

    /**
     * 허용 집합 밖의 상태가 있으면 가장 "센" 것(SUCCESS > FAILURE > EXECUTING > 나머지)을 돌려준다. 모두 허용되면 null.
     */
    private PaymentStatus firstStatusOutside(List<LockedPaymentOrder> paymentOrders, Set<PaymentStatus> allowed) {
        Set<PaymentStatus> outside = paymentOrders.stream()
                .map(LockedPaymentOrder::status)
                .filter(status -> !allowed.contains(status))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PaymentStatus.class)));
        for (PaymentStatus status : List.of(PaymentStatus.SUCCESS, PaymentStatus.FAILURE, PaymentStatus.EXECUTING,
                PaymentStatus.UNKNOWN, PaymentStatus.NOT_STARTED)) {
            if (outside.contains(status)) {
                return status;
            }
        }
        return null;
    }

    private PaymentAlreadyProcessedException alreadyProcessed(PaymentStatus status) {
        return switch (status) {
            case SUCCESS -> new PaymentAlreadyProcessedException("이미 처리 성공한 결제 입니다.", PaymentStatus.SUCCESS);
            case FAILURE -> new PaymentAlreadyProcessedException("이미 처리 실패한 결제 입니다.", PaymentStatus.FAILURE);
            case EXECUTING -> new PaymentAlreadyProcessedException("이미 승인 처리 중인 결제 입니다.", PaymentStatus.EXECUTING);
            default -> throw new IllegalArgumentException("이미 처리된 상태가 아닙니다. status: " + status);
        };
    }

    private PaymentEventMessage createSuccessEventMessage(
            PaymentStatusUpdateCommand command,
            List<LockedPaymentOrder> paymentOrders
    ) {
        List<PaymentOrder> orders = paymentOrders.stream()
                .map(it -> new PaymentOrder(
                        it.id(), null, it.sellerId(), it.productId(), command.orderId(), it.amount(),
                        PaymentStatus.SUCCESS, false, false))
                .toList();
        return PaymentEventMessage.paymentConfirmationSuccess(
                command.orderId(),
                command.paymentKey(),
                orders,
                partitionKeyUtil.createPartitionKey(command.orderId().hashCode())
        );
    }

    private Mono<List<LockedPaymentOrder>> lockPaymentOrders(String orderId) {
        return databaseClient.sql(SELECT_PAYMENT_ORDERS_FOR_UPDATE_QUERY)
                .bind("orderId", orderId)
                .fetch()
                .all()
                .map(row -> new LockedPaymentOrder(
                        ((Number) row.get("id")).longValue(),
                        ((Number) row.get("seller_id")).longValue(),
                        ((Number) row.get("product_id")).longValue(),
                        ((BigDecimal) row.get("amount")).longValueExact(),
                        PaymentStatus.get((String) row.get("payment_order_status")),
                        ((Number) row.get("failed_count")).intValue(),
                        ((Number) row.get("threshold")).intValue(),
                        ((Number) row.get("stale")).intValue() == 1
                ))
                .collectList();
    }

    private Mono<Long> insertPaymentHistory(List<LockedPaymentOrder> paymentOrders, PaymentStatus status, String reason) {
        if (paymentOrders.isEmpty()) {
            return Mono.just(0L);
        }

        // 실패 사유에는 따옴표 같은 문자가 들어올 수 있다(예: Toss 메시지 "':' 를 포함해..."). 문자열로 이어 붙이지 않고 바인딩한다.
        String safeReason = reason.length() > HISTORY_REASON_MAX_LENGTH
                ? reason.substring(0, HISTORY_REASON_MAX_LENGTH)
                : reason;
        String valueClauses = IntStream.range(0, paymentOrders.size())
                .mapToObj(i -> "(:paymentOrderId%d, :previousStatus%d, :newStatus%d, :reason%d)".formatted(i, i, i, i))
                .collect(Collectors.joining(", "));

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(insertPaymentHistoryQuery(valueClauses));
        for (int i = 0; i < paymentOrders.size(); i++) {
            LockedPaymentOrder paymentOrder = paymentOrders.get(i);
            spec = spec.bind("paymentOrderId" + i, paymentOrder.id())
                    .bind("previousStatus" + i, paymentOrder.status().name())
                    .bind("newStatus" + i, status.name())
                    .bind("reason" + i, safeReason);
        }
        return spec.fetch().rowsUpdated();
    }

    private Mono<Long> updatePaymentOrderStatus(String orderId, PaymentStatus status, Set<PaymentStatus> from) {
        String fromClause = from.stream().map(it -> "'" + it.name() + "'").collect(Collectors.joining(", "));
        return databaseClient.sql(updatePaymentOrderStatusQuery(fromClause))
                .bind("orderId", orderId)
                .bind("status", status.name())
                .fetch()
                .rowsUpdated();
    }

    private Mono<Long> updatePaymentKey(String orderId, String paymentKey) {
        return databaseClient.sql(UPDATE_PAYMENT_KEY_QUERY)
                .bind("orderId", orderId)
                .bind("paymentKey", paymentKey)
                .fetch()
                .rowsUpdated();
    }

    private Mono<Long> updatePaymentEventExtraDetails(PaymentStatusUpdateCommand command) {
        PaymentExtraDetails extraDetails = command.extraDetails();
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(UPDATE_PAYMENT_EVENT_EXTRA_DETAILS_QUERY)
                .bind("orderName", extraDetails.orderName())
                .bind("method", extraDetails.method().name())
                .bind("orderId", command.orderId())
                .bind("type", extraDetails.type().name());
        // 가상계좌 등은 approvedAt 이 비어 올 수 있다. R2DBC 는 null 을 bind 로 받지 않으므로 bindNull 을 쓴다.
        spec = extraDetails.approvedAt() == null
                ? spec.bindNull("approvedAt", LocalDateTime.class)
                : spec.bind("approvedAt", extraDetails.approvedAt());
        spec = extraDetails.pspRawData() == null
                ? spec.bindNull("pspRawData", String.class)
                : spec.bind("pspRawData", extraDetails.pspRawData());
        return spec.fetch().rowsUpdated();
    }

    private Mono<Long> incrementPaymentOrderFailedCount(PaymentStatusUpdateCommand command) {
        return databaseClient.sql(INCREMENT_PAYMENT_ORDER_FAILED_COUNT_QUERY)
                .bind("orderId", command.orderId())
                .fetch()
                .rowsUpdated();
    }

    private record LockedPaymentOrder(
            long id,
            long sellerId,
            long productId,
            long amount,
            PaymentStatus status,
            int failedCount,
            int threshold,
            boolean stale
    ) {
    }

    // stale: EXECUTING 에 3분 넘게 머물렀는지. 시각 비교는 DB 시계(NOW())로 한다.
    private static final String SELECT_PAYMENT_ORDERS_FOR_UPDATE_QUERY = """
            SELECT id, seller_id, product_id, amount, payment_order_status, failed_count, threshold,
                   (updated_at <= NOW() - INTERVAL 3 MINUTE) AS stale
            FROM payment_orders
            WHERE order_id = :orderId
            ORDER BY id
            FOR UPDATE
            """;

    private static String insertPaymentHistoryQuery(String valueClauses) {
        return """
                INSERT INTO payment_order_histories (payment_order_id, previous_status, new_status, reason)
                VALUES %s
                """.formatted(valueClauses);
    }

    private static String updatePaymentOrderStatusQuery(String fromClause) {
        return """
                UPDATE payment_orders
                SET payment_order_status = :status, updated_at = CURRENT_TIMESTAMP
                WHERE order_id = :orderId
                  AND payment_order_status IN (%s)
                """.formatted(fromClause);
    }

    private static final String UPDATE_PAYMENT_KEY_QUERY = """
            UPDATE payment_events
            SET payment_key = :paymentKey
            WHERE order_id = :orderId
            """;

    private static final String UPDATE_PAYMENT_EVENT_EXTRA_DETAILS_QUERY = """
            UPDATE payment_events
            SET order_name = :orderName, method = :method, approved_at = :approvedAt, type = :type, updated_at = CURRENT_TIMESTAMP, psp_raw_data = :pspRawData
            WHERE order_id = :orderId
            """;

    private static final String INCREMENT_PAYMENT_ORDER_FAILED_COUNT_QUERY = """
            UPDATE payment_orders
            SET failed_count = failed_count + 1
            WHERE order_id = :orderId
            """;
}
