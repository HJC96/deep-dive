package dev.deepdive.paymentsystem.payment.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

// 접근자가 xxx() 스타일이라 Jackson 기본 getter 탐지에 안 걸린다. 필드 직접 노출로 직렬화한다.
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class PaymentConfirmationResult {

    private final PaymentStatus status;
    private final PaymentFailure failure;
    private final String message;

    public PaymentConfirmationResult(PaymentStatus status, PaymentFailure failure) {
        if (status == PaymentStatus.FAILURE && failure == null) {
            throw new IllegalArgumentException("결제 상태 FAILURE 일 때 PaymentFailure 는 null 값이 될 수 없습니다.");
        }
        this.status = status;
        this.failure = failure;
        this.message = switch (status) {
            case SUCCESS -> "결제 처리에 성공하였습니다.";
            case FAILURE -> "결제 처리에 실패하였습니다.";
            case UNKNOWN -> "결제 처리 중에 알 수 없는 에러가 발생하였습니다.";
            default -> throw new IllegalStateException("현재 결제 상태 (status: " + status + ") 는 올바르지 않은 상태입니다.");
        };
    }

    public PaymentConfirmationResult(PaymentStatus status) {
        this(status, null);
    }

    public PaymentStatus status() {
        return status;
    }

    public PaymentFailure failure() {
        return failure;
    }

    public String message() {
        return message;
    }
}
