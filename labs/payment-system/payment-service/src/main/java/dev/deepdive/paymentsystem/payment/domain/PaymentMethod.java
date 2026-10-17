package dev.deepdive.paymentsystem.payment.domain;

import java.util.Arrays;

/**
 * Toss Payment 객체의 {@code method} 값. Toss는 한글 문자열("카드", "간편결제" …)로 내려준다.
 * 상수 이름(CARD 등)으로 와도 받아준다.
 */
public enum PaymentMethod {
    CARD("카드"),
    VIRTUAL_ACCOUNT("가상계좌"),
    EASY_PAY("간편결제"),
    MOBILE_PHONE("휴대폰"),
    TRANSFER("계좌이체"),
    CULTURE_GIFT_CERTIFICATE("문화상품권"),
    BOOK_GIFT_CERTIFICATE("도서문화상품권"),
    GAME_GIFT_CERTIFICATE("게임문화상품권"),
    // PSP 승인이 끝난 뒤(돈이 이미 움직인 뒤) 모르는 값 때문에 결제를 실패시키지 않으려는 대체 값.
    UNKNOWN("알 수 없음");

    private final String method;

    PaymentMethod(String method) {
        this.method = method;
    }

    public String method() {
        return method;
    }

    /**
     * 모르는 값이면 예외 대신 {@link #UNKNOWN} 을 돌려준다. 호출하는 어댑터가 경고 로그를 남긴다.
     */
    public static PaymentMethod get(String method) {
        return Arrays.stream(values())
                .filter(it -> it != UNKNOWN)
                .filter(it -> it.method.equals(method) || it.name().equals(method))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
