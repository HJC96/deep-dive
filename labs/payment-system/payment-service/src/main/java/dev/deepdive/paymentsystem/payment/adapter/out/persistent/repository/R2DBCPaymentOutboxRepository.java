package dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deepdive.paymentsystem.payment.adapter.out.persistent.util.MySQLDateTimeFormatter;
import dev.deepdive.paymentsystem.payment.adapter.out.stream.util.PartitionKeyUtil;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import dev.deepdive.paymentsystem.payment.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.payment.domain.PaymentEventMessageType;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Repository
public class R2DBCPaymentOutboxRepository implements PaymentOutboxRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final DatabaseClient databaseClient;
    private final PartitionKeyUtil partitionKeyUtil;
    private final ObjectMapper objectMapper;

    public R2DBCPaymentOutboxRepository(
            DatabaseClient databaseClient,
            PartitionKeyUtil partitionKeyUtil,
            ObjectMapper objectMapper
    ) {
        this.databaseClient = databaseClient;
        this.partitionKeyUtil = partitionKeyUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<PaymentEventMessage> insertOutbox(PaymentStatusUpdateCommand command) {
        if (command.status() != PaymentStatus.SUCCESS) {
            throw new IllegalArgumentException("아웃박스에는 SUCCESS 상태의 결제만 기록한다. status: " + command.status());
        }

        PaymentEventMessage paymentEventMessage = createPaymentEventMessage(command);

        return databaseClient.sql(INSERT_OUTBOX_QUERY)
                .bind("idempotencyKey", paymentEventMessage.payload().get("orderId"))
                .bind("partitionKey", paymentEventMessage.metadata().getOrDefault("partitionKey", 0))
                .bind("type", paymentEventMessage.type().name())
                .bind("payload", writeValueAsString(paymentEventMessage.payload()))
                .bind("metadata", writeValueAsString(paymentEventMessage.metadata()))
                .fetch()
                .rowsUpdated()
                .thenReturn(paymentEventMessage);
    }

    @Override
    public Mono<Boolean> markMessageAsSent(String idempotencyKey, PaymentEventMessageType type) {
        return databaseClient.sql(UPDATE_OUTBOX_MESSAGE_AS_SENT_QUERY)
                .bind("idempotencyKey", idempotencyKey)
                .bind("type", type.name())
                .fetch()
                .rowsUpdated()
                .thenReturn(true);
    }

    @Override
    public Mono<Boolean> markMessageAsFailure(String idempotencyKey, PaymentEventMessageType type) {
        return databaseClient.sql(UPDATE_OUTBOX_MESSAGE_AS_FAILURE_QUERY)
                .bind("idempotencyKey", idempotencyKey)
                .bind("type", type.name())
                .fetch()
                .rowsUpdated()
                .thenReturn(true);
    }

    @Override
    public Flux<PaymentEventMessage> getPendingPaymentOutboxes() {
        return databaseClient.sql(SELECT_PENDING_PAYMENT_OUTBOX_QUERY)
                .bind("createdAt", LocalDateTime.now().format(MySQLDateTimeFormatter.MYSQL_DATE_TIME))
                .fetch()
                .all()
                .map(row -> new PaymentEventMessage(
                        PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS,
                        readMap((String) row.get("payload")),
                        readMap((String) row.get("metadata"))
                ));
    }

    private PaymentEventMessage createPaymentEventMessage(PaymentStatusUpdateCommand command) {
        return new PaymentEventMessage(
                PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS,
                Map.of("orderId", command.orderId()),
                Map.of("partitionKey", partitionKeyUtil.createPartitionKey(command.orderId().hashCode()))
        );
    }

    private String writeValueAsString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("아웃박스 payload 직렬화 실패", e);
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("아웃박스 payload 역직렬화 실패", e);
        }
    }

    private static final String INSERT_OUTBOX_QUERY = """
            INSERT INTO outboxes (idempotency_key, type, partition_key, payload, metadata)
            VALUES (:idempotencyKey, :type, :partitionKey, :payload, :metadata)
            """;

    private static final String UPDATE_OUTBOX_MESSAGE_AS_SENT_QUERY = """
            UPDATE outboxes
            SET status = 'SUCCESS'
            WHERE idempotency_key = :idempotencyKey
              AND type = :type
            """;

    private static final String UPDATE_OUTBOX_MESSAGE_AS_FAILURE_QUERY = """
            UPDATE outboxes
            SET status = 'FAILURE'
            WHERE idempotency_key = :idempotencyKey
              AND type = :type
            """;

    private static final String SELECT_PENDING_PAYMENT_OUTBOX_QUERY = """
            SELECT *
            FROM outboxes
            WHERE (status = 'INIT' OR status = 'FAILURE')
              AND created_at <= :createdAt - INTERVAL 1 MINUTE
              AND type = 'PAYMENT_CONFIRMATION_SUCCESS'
            """;
}
