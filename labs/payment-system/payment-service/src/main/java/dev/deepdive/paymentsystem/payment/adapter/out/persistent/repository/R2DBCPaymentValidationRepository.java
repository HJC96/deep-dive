package dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository;

import dev.deepdive.paymentsystem.payment.adapter.out.persistent.exception.PaymentValidationException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Repository
public class R2DBCPaymentValidationRepository implements PaymentValidationRepository {

    private final DatabaseClient databaseClient;

    public R2DBCPaymentValidationRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Boolean> isValid(String orderId, long amount) {
        return databaseClient.sql(SELECT_PAYMENT_TOTAL_AMOUNT_QUERY)
                .bind("orderId", orderId)
                .fetch()
                .first()
                .<Boolean>handle((row, sink) -> {
                    if (((BigDecimal) row.get("total_amount")).longValue() == amount) {
                        sink.next(true);
                    } else {
                        sink.error(new PaymentValidationException(
                                "결제 (orderId: " + orderId + ") 에서 금액 (amount: " + amount + ")이 올바르지 않습니다."));
                    }
                });
    }

    private static final String SELECT_PAYMENT_TOTAL_AMOUNT_QUERY = """
            SELECT SUM(amount) as total_amount
            FROM payment_orders
            WHERE order_id = :orderId
            """;
}
