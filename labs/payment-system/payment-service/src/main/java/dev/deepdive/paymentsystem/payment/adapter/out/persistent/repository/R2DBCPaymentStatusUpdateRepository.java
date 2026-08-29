package dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository;

import dev.deepdive.paymentsystem.payment.adapter.out.persistent.exception.PaymentAlreadyProcessedException;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class R2DBCPaymentStatusUpdateRepository implements PaymentStatusUpdateRepository {

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;

    public R2DBCPaymentStatusUpdateRepository(
            DatabaseClient databaseClient,
            TransactionalOperator transactionalOperator
    ) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    public Mono<Boolean> updatePaymentStatusToExecuting(String orderId, String paymentKey) {
        return checkPreviousPaymentOrderStatus(orderId)
                .flatMap(it -> insertPaymentHistory(it, PaymentStatus.EXECUTING, "PAYMENT_CONFIRMATION_START"))
                .flatMap(it -> updatePaymentOrderStatus(orderId, PaymentStatus.EXECUTING))
                .flatMap(it -> updatePaymentKey(orderId, paymentKey))
                .as(transactionalOperator::transactional)
                .thenReturn(true);
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

    private Mono<List<Map.Entry<Long, String>>> checkPreviousPaymentOrderStatus(String orderId) {
        return selectPaymentOrderStatus(orderId)
                .<Map.Entry<Long, String>>handle((paymentOrder, sink) -> {
                    String status = paymentOrder.getValue();
                    if (status.equals(PaymentStatus.NOT_STARTED.name())
                            || status.equals(PaymentStatus.UNKNOWN.name())
                            || status.equals(PaymentStatus.EXECUTING.name())) {
                        sink.next(paymentOrder);
                    } else if (status.equals(PaymentStatus.SUCCESS.name())) {
                        sink.error(new PaymentAlreadyProcessedException("이미 처리 성공한 결제 입니다.", PaymentStatus.SUCCESS));
                    } else if (status.equals(PaymentStatus.FAILURE.name())) {
                        sink.error(new PaymentAlreadyProcessedException("이미 처리 실패한 결제 입니다.", PaymentStatus.FAILURE));
                    }
                })
                .collectList();
    }

    private Flux<Map.Entry<Long, String>> selectPaymentOrderStatus(String orderId) {
        return databaseClient.sql(SELECT_PAYMENT_ORDER_STATUS_QUERY)
                .bind("orderId", orderId)
                .fetch()
                .all()
                .map(row -> Map.entry((Long) row.get("id"), (String) row.get("payment_order_status")));
    }

    private Mono<Long> insertPaymentHistory(
            List<Map.Entry<Long, String>> paymentOrderIdToStatus,
            PaymentStatus status,
            String reason
    ) {
        if (paymentOrderIdToStatus.isEmpty()) {
            return Mono.empty();
        }

        String valuesClauses = paymentOrderIdToStatus.stream()
                .map(it -> "( %d, '%s', '%s', '%s' )".formatted(it.getKey(), it.getValue(), status, reason))
                .collect(Collectors.joining(", "));

        return databaseClient.sql(insertPaymentHistoryQuery(valuesClauses))
                .fetch()
                .rowsUpdated();
    }

    private Mono<Long> updatePaymentOrderStatus(String orderId, PaymentStatus status) {
        return databaseClient.sql(UPDATE_PAYMENT_ORDER_STATUS_QUERY)
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

    private Mono<Boolean> updatePaymentStatusToSuccess(PaymentStatusUpdateCommand command) {
        return selectPaymentOrderStatus(command.orderId())
                .collectList()
                .flatMap(it -> insertPaymentHistory(it, command.status(), "PAYMENT_CONFIRMATION_DONE"))
                .flatMap(it -> updatePaymentOrderStatus(command.orderId(), command.status()))
                .flatMap(it -> updatePaymentEventExtraDetails(command))
                .as(transactionalOperator::transactional)
                .thenReturn(true);
    }

    private Mono<Boolean> updatePaymentStatusToFailure(PaymentStatusUpdateCommand command) {
        return selectPaymentOrderStatus(command.orderId())
                .collectList()
                .flatMap(it -> insertPaymentHistory(it, command.status(), String.valueOf(command.failure())))
                .flatMap(it -> updatePaymentOrderStatus(command.orderId(), command.status()))
                .as(transactionalOperator::transactional)
                .thenReturn(true);
    }

    private Mono<Boolean> updatePaymentStatusToUnknown(PaymentStatusUpdateCommand command) {
        return selectPaymentOrderStatus(command.orderId())
                .collectList()
                .flatMap(it -> insertPaymentHistory(it, command.status(), String.valueOf(command.failure())))
                .flatMap(it -> updatePaymentOrderStatus(command.orderId(), command.status()))
                .flatMap(it -> incrementPaymentOrderFailedCount(command))
                .as(transactionalOperator::transactional)
                .thenReturn(true);
    }

    private Mono<Long> updatePaymentEventExtraDetails(PaymentStatusUpdateCommand command) {
        return databaseClient.sql(UPDATE_PAYMENT_EVENT_EXTRA_DETAILS_QUERY)
                .bind("orderName", command.extraDetails().orderName())
                .bind("method", command.extraDetails().method().name())
                .bind("approvedAt", command.extraDetails().approvedAt())
                .bind("orderId", command.orderId())
                .bind("type", command.extraDetails().type().name())
                .bind("pspRawData", command.extraDetails().pspRawData())
                .fetch()
                .rowsUpdated();
    }

    private Mono<Long> incrementPaymentOrderFailedCount(PaymentStatusUpdateCommand command) {
        return databaseClient.sql(INCREMENT_PAYMENT_ORDER_FAILED_COUNT_QUERY)
                .bind("orderId", command.orderId())
                .fetch()
                .rowsUpdated();
    }

    private static final String SELECT_PAYMENT_ORDER_STATUS_QUERY = """
            SELECT id, payment_order_status
            FROM payment_orders
            WHERE order_id = :orderId
            """;

    private static String insertPaymentHistoryQuery(String valueClauses) {
        return """
                INSERT INTO payment_order_histories (payment_order_id, previous_status, new_status, reason)
                VALUES %s
                """.formatted(valueClauses);
    }

    private static final String UPDATE_PAYMENT_ORDER_STATUS_QUERY = """
            UPDATE payment_orders
            SET payment_order_status = :status, updated_at = CURRENT_TIMESTAMP
            WHERE order_id = :orderId
            """;

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
