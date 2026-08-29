package dev.deepdive.paymentsystem.payment.domain;

import java.util.Arrays;

public enum PaymentType {
    NORMAL("일반 결제");

    PaymentType(String description) {
    }

    public static PaymentType get(String type) {
        return Arrays.stream(values())
                .filter(it -> it.name().equals(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("PaymentType (type: " + type + ") 은 올바르지 않은 결제 타입입니다."));
    }
}
