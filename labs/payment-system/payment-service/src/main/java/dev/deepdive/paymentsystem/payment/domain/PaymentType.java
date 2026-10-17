package dev.deepdive.paymentsystem.payment.domain;

import java.util.Arrays;

/**
 * Toss Payment 객체의 {@code type} 값.
 */
public enum PaymentType {
    NORMAL("일반 결제"),
    BILLING("자동 결제"),
    BRANDPAY("브랜드페이"),
    // PSP 승인 이후 모르는 값이 와도 결제를 실패시키지 않으려는 대체 값.
    UNKNOWN("알 수 없음");

    PaymentType(String description) {
    }

    /**
     * 모르는 값이면 예외 대신 {@link #UNKNOWN} 을 돌려준다. 호출하는 어댑터가 경고 로그를 남긴다.
     */
    public static PaymentType get(String type) {
        return Arrays.stream(values())
                .filter(it -> it != UNKNOWN)
                .filter(it -> it.name().equals(type))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
