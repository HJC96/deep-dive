package dev.deepdive.paymentsystem.ledger.domain;

import java.util.Map;

public record LedgerEventMessage(
        LedgerEventMessageType type,
        Map<String, Object> payload,
        Map<String, Object> metadata
) {

    public LedgerEventMessage {
        if (payload == null) {
            payload = Map.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
