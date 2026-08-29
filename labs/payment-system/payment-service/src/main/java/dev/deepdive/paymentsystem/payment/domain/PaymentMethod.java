package dev.deepdive.paymentsystem.payment.domain;

import java.util.Arrays;

public enum PaymentMethod {
    EASY_PAY("간편결제");

    private final String method;

    PaymentMethod(String method) {
        this.method = method;
    }

    public static PaymentMethod get(String method) {
        return Arrays.stream(values())
                .filter(it -> it.method.equals(method))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Payment Method (method: " + method + ") 는 올바르지 않은 결제 방법입니다."));
    }
}
