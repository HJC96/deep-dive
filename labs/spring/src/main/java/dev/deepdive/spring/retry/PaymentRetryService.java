package dev.deepdive.spring.retry;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class PaymentRetryService {

    private final PaymentGatewayClient paymentGatewayClient;

    public PaymentRetryService(PaymentGatewayClient paymentGatewayClient) {
        this.paymentGatewayClient = paymentGatewayClient;
    }

    @Retryable(
            retryFor = PaymentException.class,
            noRetryFor = InvalidPaymentRequestException.class,
            notRecoverable = UnknownPaymentStateException.class,
            maxAttemptsExpression = "${payment.retry.max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${payment.retry.initial-delay:50}",
                    maxDelayExpression = "${payment.retry.max-delay:500}",
                    multiplierExpression = "${payment.retry.multiplier:2.0}",
                    randomExpression = "${payment.retry.random:true}"
            )
    )
    public PaymentResult requestPayment(PaymentRequest request) {
        return paymentGatewayClient.requestPayment(request);
    }

    @Recover
    public PaymentResult recover(TemporaryPaymentException exception, PaymentRequest request) {
        return PaymentResult.reviewRequired(request.orderId());
    }

    @Recover
    public PaymentResult recover(InvalidPaymentRequestException exception, PaymentRequest request) {
        return PaymentResult.rejected(request.orderId());
    }

    // UnknownPaymentStateException에 notRecoverable이 없다면 이 메서드가 선택된다.
    @Recover
    public PaymentResult recover(PaymentException exception, PaymentRequest request) {
        return PaymentResult.reviewRequired(request.orderId());
    }
}
