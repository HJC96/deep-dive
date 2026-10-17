package dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository;

import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import dev.deepdive.paymentsystem.payment.domain.PaymentOrder;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import dev.deepdive.paymentsystem.payment.domain.PendingPaymentEvent;
import dev.deepdive.paymentsystem.payment.domain.PendingPaymentOrder;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Repository
public class R2DBCPaymentRepository implements PaymentRepository {

    /**
     * 복구 한 회차에 가져올 결제(payment_event) 수. 주문 행이 아니라 결제 단위로 자른다.
     */
    static final int PENDING_PAYMENT_EVENT_LIMIT = 10;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;

    public R2DBCPaymentRepository(DatabaseClient databaseClient, TransactionalOperator transactionalOperator) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    public Mono<Void> save(PaymentEvent paymentEvent) {
        return insertPaymentEvent(paymentEvent)
                .flatMap(rows -> selectPaymentEventId())
                .flatMap(paymentEventId -> insertPaymentOrders(paymentEvent, paymentEventId))
                .as(transactionalOperator::transactional)
                .then();
    }

    @Override
    public Flux<PendingPaymentEvent> getPendingPayments() {
        return databaseClient.sql(SELECT_PENDING_PAYMENT_QUERY)
                .fetch()
                .all()
                .groupBy(row -> ((Number) row.get("payment_event_id")).longValue())
                .flatMap(grouped -> grouped.collectList().map(rows -> {
                    List<PendingPaymentOrder> pendingPaymentOrders = rows.stream()
                            .map(row -> new PendingPaymentOrder(
                                    ((Number) row.get("payment_order_id")).longValue(),
                                    PaymentStatus.get((String) row.get("payment_order_status")),
                                    ((Number) row.get("amount")).longValue(),
                                    ((Number) row.get("failed_count")).byteValue(),
                                    ((Number) row.get("threshold")).byteValue()
                            ))
                            .toList();

                    Map<String, Object> first = rows.get(0);
                    return new PendingPaymentEvent(
                            grouped.key(),
                            (String) first.get("payment_key"),
                            (String) first.get("order_id"),
                            pendingPaymentOrders
                    );
                }));
    }

    @Override
    public Mono<Void> completeWalletUpdate(String orderId) {
        return markAndCompleteIfDone(UPDATE_PAYMENT_ORDER_WALLET_DONE_QUERY, orderId);
    }

    @Override
    public Mono<Void> completeLedgerUpdate(String orderId) {
        return markAndCompleteIfDone(UPDATE_PAYMENT_ORDER_LEDGER_DONE_QUERY, orderId);
    }

    /**
     * 자기 플래그를 켜고, 같은 트랜잭션에서 "모든 주문의 두 플래그가 켜졌는지"를 조건으로 한 UPDATE 로 결제 완료를 표시한다.
     * Wallet·Ledger 완료가 동시에 와도 첫 UPDATE 가 같은 주문 행에 배타 락을 잡으므로 둘은 직렬화되고,
     * 뒤에 커밋하는 쪽의 두 번째 UPDATE 는 앞선 쪽이 커밋한 플래그를 본다(락킹 읽기는 최신 커밋 값을 읽는다).
     */
    private Mono<Void> markAndCompleteIfDone(String markQuery, String orderId) {
        return databaseClient.sql(markQuery)
                .bind("orderId", orderId)
                .fetch()
                .rowsUpdated()
                .then(databaseClient.sql(UPDATE_PAYMENT_EVENT_COMPLETE_IF_DONE_QUERY)
                        .bind("orderId", orderId)
                        .bind("orderIdForOrders", orderId)
                        .fetch()
                        .rowsUpdated())
                .as(transactionalOperator::transactional)
                .then();
    }

    private Mono<Long> insertPaymentEvent(PaymentEvent paymentEvent) {
        return databaseClient.sql(INSERT_PAYMENT_EVENT_QUERY)
                .bind("buyerId", paymentEvent.buyerId())
                .bind("orderName", paymentEvent.orderName())
                .bind("orderId", paymentEvent.orderId())
                .fetch()
                .rowsUpdated();
    }

    private Mono<Long> selectPaymentEventId() {
        return databaseClient.sql(LAST_INSERT_ID_QUERY)
                .fetch()
                .first()
                .map(row -> ((BigInteger) row.get("LAST_INSERT_ID()")).longValue());
    }

    private Mono<Long> insertPaymentOrders(PaymentEvent paymentEvent, Long paymentEventId) {
        List<PaymentOrder> paymentOrders = paymentEvent.paymentOrders();
        // 값을 문자열로 이어 붙이지 않고 행마다 이름 붙은 파라미터로 바인딩한다.
        String valueClauses = IntStream.range(0, paymentOrders.size())
                .mapToObj(i -> "(:paymentEventId%d, :sellerId%d, :orderId%d, :productId%d, :amount%d, :status%d)"
                        .formatted(i, i, i, i, i, i))
                .collect(Collectors.joining(", "));

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(insertPaymentOrderQuery(valueClauses));
        for (int i = 0; i < paymentOrders.size(); i++) {
            PaymentOrder paymentOrder = paymentOrders.get(i);
            spec = spec.bind("paymentEventId" + i, paymentEventId)
                    .bind("sellerId" + i, paymentOrder.sellerId())
                    .bind("orderId" + i, paymentOrder.orderId())
                    .bind("productId" + i, paymentOrder.productId())
                    .bind("amount" + i, paymentOrder.amount())
                    .bind("status" + i, paymentOrder.paymentStatus().name());
        }

        return spec.fetch().rowsUpdated();
    }

    private static final String INSERT_PAYMENT_EVENT_QUERY = """
            INSERT INTO payment_events (buyer_id, order_name, order_id)
            VALUES (:buyerId, :orderName, :orderId)
            """;

    private static final String LAST_INSERT_ID_QUERY = """
            SELECT LAST_INSERT_ID()
            """;

    /*
     * 예전 쿼리는 주문 행(payment_orders)에 LIMIT 10 을 걸어서, 주문이 여러 개인 결제는 일부 주문만 잘려 들어올 수 있었다.
     * 그러면 금액 합이 어긋나 검증에서 FAILURE 가 났다. 지금은 대상 결제 id 를 먼저 결제 단위로 골라(정렬 + LIMIT)
     * 그 결제의 주문을 전부 다시 붙인다. 시각 비교는 DB 시계(NOW())로 해서 JVM·DB 시간대가 달라도 어긋나지 않게 한다.
     */
    private static final String SELECT_PENDING_PAYMENT_QUERY = """
            SELECT pe.id AS payment_event_id, pe.payment_key, pe.order_id,
                   po.id AS payment_order_id, po.payment_order_status, po.amount, po.failed_count, po.threshold
            FROM (
                SELECT DISTINCT target.payment_event_id
                FROM payment_orders target
                WHERE (target.payment_order_status = 'UNKNOWN'
                       OR (target.payment_order_status = 'EXECUTING' AND target.updated_at <= NOW() - INTERVAL 3 MINUTE))
                  AND target.failed_count < target.threshold
                ORDER BY target.payment_event_id
                LIMIT %d
            ) pending
            INNER JOIN payment_events pe ON pe.id = pending.payment_event_id
            INNER JOIN payment_orders po ON po.payment_event_id = pe.id
            ORDER BY pe.id, po.id
            """.formatted(PENDING_PAYMENT_EVENT_LIMIT);

    private static final String UPDATE_PAYMENT_ORDER_LEDGER_DONE_QUERY = """
            UPDATE payment_orders
            SET ledger_updated = 1
            WHERE order_id = :orderId
              AND payment_order_status = 'SUCCESS'
            """;

    private static final String UPDATE_PAYMENT_ORDER_WALLET_DONE_QUERY = """
            UPDATE payment_orders
            SET wallet_updated = 1
            WHERE order_id = :orderId
              AND payment_order_status = 'SUCCESS'
            """;

    private static final String UPDATE_PAYMENT_EVENT_COMPLETE_IF_DONE_QUERY = """
            UPDATE payment_events
            SET is_payment_done = 1
            WHERE order_id = :orderId
              AND is_payment_done = 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM payment_orders po
                  WHERE po.order_id = :orderIdForOrders
                    AND (po.wallet_updated = 0 OR po.ledger_updated = 0)
              )
            """;

    private static String insertPaymentOrderQuery(String valueClauses) {
        return """
                INSERT INTO payment_orders (payment_event_id, seller_id, order_id, product_id, amount, payment_order_status)
                VALUES %s
                """.formatted(valueClauses);
    }
}
