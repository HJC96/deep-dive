package dev.deepdive.paymentsystem.payment.test;

import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import dev.deepdive.paymentsystem.payment.domain.PaymentOrder;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

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

        List<PaymentOrder> paymentOrders = rows.stream()
                .map(row -> new PaymentOrder(
                        ((Number) row.get("seller_id")).longValue(),
                        (String) row.get("order_id"),
                        ((Number) row.get("product_id")).longValue(),
                        ((Number) row.get("amount")).longValue(),
                        PaymentStatus.get((String) row.get("payment_order_status"))
                ))
                .toList();

        Map<String, Object> first = rows.get(0);
        return new PaymentEvent(
                ((Number) first.get("buyer_id")).longValue(),
                (String) first.get("order_name"),
                (String) first.get("order_id"),
                paymentOrders
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
            SELECT pe.order_id, pe.order_name, pe.buyer_id,
                   po.seller_id, po.product_id, po.amount, po.payment_order_status
            FROM payment_events pe
            INNER JOIN payment_orders po ON pe.order_id = po.order_id
            WHERE pe.order_id = :orderId
            """;
}
