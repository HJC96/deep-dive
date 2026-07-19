package dev.deepdive.spring.retry;

import org.springframework.stereotype.Component;

@Component
public class PaymentGatewayClient {

    public PaymentResult requestPayment(PaymentRequest request) {
        return PaymentResult.completed(request.orderId());
    }
}
