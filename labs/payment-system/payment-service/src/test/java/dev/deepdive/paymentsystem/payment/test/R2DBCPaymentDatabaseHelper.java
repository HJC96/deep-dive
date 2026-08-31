package dev.deepdive.paymentsystem.payment.test;

import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import dev.deepdive.paymentsystem.payment.domain.PaymentMethod;
import dev.deepdive.paymentsystem.payment.domain.PaymentOrder;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import dev.deepdive.paymentsystem.payment.domain.PaymentType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class R2DBCPaymentDatabaseHelper implements PaymentDatabaseHelper {

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;

    public R2DBCPaymentDatabaseHelper(DatabaseClient databaseClient, TransactionalOperator transactionalOperator) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    public PaymentEvent getPayments(String orderId) {
        return databaseClient.sql(SELECT_PAYMENT_QUERY)
                .bind("orderId", orderId)
                .fetch()
                .all()
                .collectList()
                .mapNotNull(this::toPaymentEvent)
                .block();
    }

    private PaymentEvent toPaymentEvent(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> firstRow = rows.get(0);
        long paymentEventId = ((Number) firstRow.get("id")).longValue();

        List<PaymentOrder> paymentOrders = rows.stream()
                .map(row -> new PaymentOrder(
                        ((Number) row.get("payment_order_id")).longValue(),
                        paymentEventId,
                        ((Number) row.get("seller_id")).longValue(),
                        ((Number) row.get("product_id")).longValue(),
                        (String) row.get("order_id"),
                        ((Number) row.get("amount")).longValue(),
                        PaymentStatus.get((String) row.get("payment_order_status")),
                        ((Number) row.get("ledger_updated")).intValue() == 1,
                        ((Number) row.get("wallet_updated")).intValue() == 1
                ))
                .toList();

        return new PaymentEvent(
                paymentEventId,
                ((Number) firstRow.get("buyer_id")).longValue(),
                (String) firstRow.get("order_name"),
                (String) firstRow.get("order_id"),
                (String) firstRow.get("payment_key"),
                firstRow.get("type") == null ? null : PaymentType.valueOf((String) firstRow.get("type")),
                firstRow.get("method") == null ? null : PaymentMethod.valueOf((String) firstRow.get("method")),
                (LocalDateTime) firstRow.get("approved_at"),
                paymentOrders,
                ((Number) firstRow.get("is_payment_done")).intValue() == 1
        );
    }

    @Override
    public Mono<Void> clean() {
        return delete("payment_order_histories")
                .flatMap(rows -> delete("payment_orders"))
                .flatMap(rows -> delete("payment_events"))
                .as(transactionalOperator::transactional)
                .then();
    }

    private Mono<Long> delete(String table) {
        return databaseClient.sql("DELETE FROM " + table)
                .fetch()
                .rowsUpdated();
    }

    private static final String SELECT_PAYMENT_QUERY = """
            SELECT pe.id, pe.order_id, pe.order_name, pe.buyer_id, pe.payment_key,
                   pe.type, pe.method, pe.approved_at, pe.is_payment_done,
                   po.id AS payment_order_id, po.seller_id, po.product_id, po.amount,
                   po.payment_order_status, po.ledger_updated, po.wallet_updated
            FROM payment_events pe
            INNER JOIN payment_orders po ON pe.order_id = po.order_id
            WHERE pe.order_id = :orderId
            """;
}
