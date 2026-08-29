package dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository;

import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
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

    private static String insertPaymentOrderQuery(String valueClauses) {
        return """
                INSERT INTO payment_orders (payment_event_id, seller_id, order_id, product_id, amount, payment_order_status)
                VALUES %s
                """.formatted(valueClauses);
    }
}
