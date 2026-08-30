package dev.deepdive.paymentsystem.payment.domain;

import java.util.Map;

public record PaymentEventMessage(
        PaymentEventMessageType type,
        Map<String, Object> payload,
        Map<String, Object> metadata
) {

    public PaymentEventMessage {
        if (payload == null) {
            payload = Map.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
