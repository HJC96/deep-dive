package dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository;

import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
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

    private static String insertPaymentOrderQuery(String valueClauses) {
        return """
                INSERT INTO payment_orders (payment_event_id, seller_id, order_id, product_id, amount, payment_order_status)
                VALUES %s
                """.formatted(valueClauses);
    }
}
