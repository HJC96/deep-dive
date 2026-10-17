package dev.deepdive.paymentsystem.payment.application.exception;

/**
 * orderId 에 해당하는 결제 주문이 없을 때 던진다. 상태를 기록할 행 자체가 없으므로 DB 에 아무것도 쓰지 않고 FAILURE 로 응답한다.
 */
public class PaymentNotFoundException extends PaymentValidationException {

    public static final String ERROR_CODE = "PAYMENT_NOT_FOUND";

    public PaymentNotFoundException(String orderId) {
        super("결제 (orderId: " + orderId + ") 를 찾을 수 없습니다.");
    }
}
