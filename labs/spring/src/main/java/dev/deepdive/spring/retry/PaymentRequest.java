package dev.deepdive.spring.retry;

import org.springframework.util.Assert;

public record PaymentRequest(String orderId, String idempotencyKey) {

    public PaymentRequest {
        Assert.hasText(orderId, "orderId는 비어 있을 수 없습니다.");
        Assert.hasText(idempotencyKey, "idempotencyKey는 비어 있을 수 없습니다.");
    }
}
