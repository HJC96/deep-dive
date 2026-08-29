package dev.deepdive.paymentsystem.payment.application.port.in;

import java.util.List;

public record CheckoutCommand(
        Long cartId,
        Long buyerId,
        List<Long> productIds,
        String idempotencyKey
) {
}
