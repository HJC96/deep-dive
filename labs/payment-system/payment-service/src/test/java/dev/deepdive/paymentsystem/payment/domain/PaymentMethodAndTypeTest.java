package dev.deepdive.paymentsystem.payment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMethodAndTypeTest {

    @ParameterizedTest
    @CsvSource({
            "카드, CARD",
            "가상계좌, VIRTUAL_ACCOUNT",
            "간편결제, EASY_PAY",
            "휴대폰, MOBILE_PHONE",
            "계좌이체, TRANSFER",
            "문화상품권, CULTURE_GIFT_CERTIFICATE",
            "도서문화상품권, BOOK_GIFT_CERTIFICATE",
            "게임문화상품권, GAME_GIFT_CERTIFICATE",
            "CARD, CARD"
    })
    void should_map_toss_payment_methods(String tossValue, PaymentMethod expected) {
        assertThat(PaymentMethod.get(tossValue)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"포인트", "", "UNKNOWN", "알 수 없음"})
    void should_fall_back_to_UNKNOWN_method_instead_of_throwing(String tossValue) {
        assertThat(PaymentMethod.get(tossValue)).isEqualTo(PaymentMethod.UNKNOWN);
    }

    @ParameterizedTest
    @CsvSource({"NORMAL, NORMAL", "BILLING, BILLING", "BRANDPAY, BRANDPAY"})
    void should_map_toss_payment_types(String tossValue, PaymentType expected) {
        assertThat(PaymentType.get(tossValue)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"SOMETHING_NEW", "UNKNOWN"})
    void should_fall_back_to_UNKNOWN_type_instead_of_throwing(String tossValue) {
        assertThat(PaymentType.get(tossValue)).isEqualTo(PaymentType.UNKNOWN);
    }

    @Test
    void every_constant_name_fits_its_column() {
        // payment_events.method 는 VARCHAR(50), type 은 VARCHAR(20) 이다. 상수 이름(name())을 저장한다.
        assertThat(PaymentMethod.values()).allMatch(it -> it.name().length() <= 50);
        assertThat(PaymentType.values()).allMatch(it -> it.name().length() <= 20);
    }
}
