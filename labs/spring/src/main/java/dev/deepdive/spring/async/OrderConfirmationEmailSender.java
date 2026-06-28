package dev.deepdive.spring.async;

import org.springframework.stereotype.Component;

@Component
public class OrderConfirmationEmailSender {

    public EmailReceipt send(Order order, String runner) {
        return new EmailReceipt(
                order.id(),
                order.customerEmail(),
                "[deep-dive] 주문 확인: " + order.id(),
                order.productName() + " 주문이 완료되었습니다. 결제 금액: " + order.totalPrice() + "원",
                runner,
                Thread.currentThread().getName()
        );
    }
}
