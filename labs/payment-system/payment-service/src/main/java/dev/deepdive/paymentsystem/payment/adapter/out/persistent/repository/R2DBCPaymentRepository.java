package dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository;

import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import dev.deepdive.paymentsystem.payment.domain.PaymentOrder;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import dev.deepdive.paymentsystem.payment.domain.PendingPaymentEvent;
import dev.deepdive.paymentsystem.payment.domain.PendingPaymentOrder;
import dev.deepdive.paymentsystem.payment.adapter.out.persistent.util.MySQLDateTimeFormatter;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class R2DBCPaymentRepository implements PaymentRepository {

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
                .bind("updatedAt", LocalDateTime.now().format(MySQLDateTimeFormatter.MYSQL_DATE_TIME))
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
    public Mono<PaymentEvent> getPayment(String orderId) {
        return databaseClient.sql(SELECT_PAYMENT_EVENT_QUERY)
                .bind("orderId", orderId)
                .fetch()
                .all()
                .groupBy(row -> ((Number) row.get("payment_event_id")).longValue())
                .flatMap(grouped -> grouped.collectList().map(results -> {
                    List<PaymentOrder> paymentOrders = results.stream()
                            .map(result -> new PaymentOrder(
                                    ((Number) result.get("payment_order_id")).longValue(),
                                    grouped.key(),
                                    ((Number) result.get("seller_id")).longValue(),
                                    ((Number) result.get("product_id")).longValue(),
                                    (String) result.get("order_id"),
                                    ((Number) result.get("amount")).longValue(),
                                    PaymentStatus.get((String) result.get("payment_order_status")),
                                    ((Number) result.get("ledger_updated")).intValue() == 1,
                                    ((Number) result.get("wallet_updated")).intValue() == 1
                            ))
                            .toList();

                    Map<String, Object> first = results.get(0);
                    return new PaymentEvent(
                            grouped.key(),
                            ((Number) first.get("buyer_id")).longValue(),
                            (String) first.get("order_name"),
                            (String) first.get("order_id"),
                            paymentOrders,
                            ((Number) first.get("is_payment_done")).intValue() == 1
                    );
                }))
                .next();
    }

    @Override
    public Mono<Void> complete(PaymentEvent paymentEvent) {
        if (paymentEvent.isPaymentDone()) {
            return handlePaymentCompletion(paymentEvent);
        }
        if (paymentEvent.isLedgerUpdateDone()) {
            return handleLedgerUpdate(paymentEvent);
        }
        if (paymentEvent.isWalletUpdateDone()) {
            return handleWalletUpdate(paymentEvent);
        }
        return Mono.error(new IllegalStateException(
                "Incorrect state for PaymentEvent id: " + paymentEvent.id()));
    }

    private Mono<Void> handlePaymentCompletion(PaymentEvent paymentEvent) {
        return Mono.when(
                handleLedgerUpdate(paymentEvent),
                handleWalletUpdate(paymentEvent)
        ).then(Mono.defer(() -> completePaymentEvent(paymentEvent)));
    }

    private Mono<Void> handleLedgerUpdate(PaymentEvent paymentEvent) {
        return databaseClient.sql(UPDATE_PAYMENT_ORDER_LEDGER_DONE_QUERY)
                .bind("paymentEventId", paymentEvent.id())
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Void> handleWalletUpdate(PaymentEvent paymentEvent) {
        return databaseClient.sql(UPDATE_PAYMENT_ORDER_WALLET_DONE_QUERY)
                .bind("paymentEventId", paymentEvent.id())
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Void> completePaymentEvent(PaymentEvent paymentEvent) {
        return databaseClient.sql(UPDATE_PAYMENT_EVENT_COMPLETE_QUERY)
                .bind("paymentEventId", paymentEvent.id())
                .fetch()
                .rowsUpdated()
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
        String valueClauses = paymentEvent.paymentOrders().stream()
                .map(paymentOrder -> "(%d, %d, '%s', %d, %d, '%s')".formatted(
                        paymentEventId,
                        paymentOrder.sellerId(),
                        paymentOrder.orderId(),
                        paymentOrder.productId(),
                        paymentOrder.amount(),
                        paymentOrder.paymentStatus()
                ))
                .collect(Collectors.joining(", "));

        return databaseClient.sql(insertPaymentOrderQuery(valueClauses))
                .fetch()
                .rowsUpdated();
    }

    private static final String INSERT_PAYMENT_EVENT_QUERY = """
            INSERT INTO payment_events (buyer_id, order_name, order_id)
            VALUES (:buyerId, :orderName, :orderId)
            """;

    private static final String LAST_INSERT_ID_QUERY = """
            SELECT LAST_INSERT_ID()
            """;

    private static final String SELECT_PENDING_PAYMENT_QUERY = """
            SELECT pe.id AS payment_event_id, pe.payment_key, pe.order_id,
                   po.id AS payment_order_id, po.payment_order_status, po.amount, po.failed_count, po.threshold
            FROM payment_events pe
            INNER JOIN payment_orders po ON po.payment_event_id = pe.id
            WHERE (po.payment_order_status = 'UNKNOWN'
                   OR (po.payment_order_status = 'EXECUTING' AND po.updated_at <= :updatedAt - INTERVAL 3 MINUTE))
              AND po.failed_count < po.threshold
            LIMIT 10
            """;

    private static final String SELECT_PAYMENT_EVENT_QUERY = """
            SELECT pe.id AS payment_event_id, po.id AS payment_order_id, pe.order_id, pe.order_name, pe.buyer_id,
                   pe.is_payment_done, po.seller_id, po.product_id, po.payment_order_status, po.amount,
                   po.ledger_updated, po.wallet_updated
            FROM payment_events pe
            INNER JOIN payment_orders po ON pe.order_id = po.order_id
            WHERE pe.order_id = :orderId
            """;

    private static final String UPDATE_PAYMENT_ORDER_LEDGER_DONE_QUERY = """
            UPDATE payment_orders
            SET ledger_updated = true
            WHERE payment_event_id = :paymentEventId
            """;

    private static final String UPDATE_PAYMENT_ORDER_WALLET_DONE_QUERY = """
            UPDATE payment_orders
            SET wallet_updated = true
            WHERE payment_event_id = :paymentEventId
            """;

    private static final String UPDATE_PAYMENT_EVENT_COMPLETE_QUERY = """
            UPDATE payment_events
            SET is_payment_done = true
            WHERE id = :paymentEventId
            """;

    private static String insertPaymentOrderQuery(String valueClauses) {
        return """
                INSERT INTO payment_orders (payment_event_id, seller_id, order_id, product_id, amount, payment_order_status)
                VALUES %s
                """.formatted(valueClauses);
    }
}
