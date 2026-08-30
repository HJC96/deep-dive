package dev.deepdive.paymentsystem.payment.domain;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalEventPublisher;
import reactor.core.publisher.Mono;

@Component
public class PaymentEventMessagePublisher {

    private final TransactionalEventPublisher transactionalEventPublisher;

    public PaymentEventMessagePublisher(ApplicationEventPublisher publisher) {
        this.transactionalEventPublisher = new TransactionalEventPublisher(publisher);
    }

    public Mono<PaymentEventMessage> publishEvent(PaymentEventMessage paymentEventMessage) {
        return transactionalEventPublisher.publishEvent(paymentEventMessage)
                .thenReturn(paymentEventMessage);
    }
}
