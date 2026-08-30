package dev.deepdive.paymentsystem.ledger.domain;

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

    public String orderId() {
        return (String) payload.get("orderId");
    }
}
