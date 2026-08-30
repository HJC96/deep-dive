package dev.deepdive.paymentsystem.wallet.domain;

import java.util.Map;

public record WalletEventMessage(
        WalletEventMessageType type,
        Map<String, Object> payload,
        Map<String, Object> metadata
) {

    public WalletEventMessage {
        if (payload == null) {
            payload = Map.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
