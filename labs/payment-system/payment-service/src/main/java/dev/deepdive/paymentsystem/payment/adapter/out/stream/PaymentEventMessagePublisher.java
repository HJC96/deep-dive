package dev.deepdive.paymentsystem.payment.adapter.out.stream;

import dev.deepdive.paymentsystem.payment.domain.PaymentEventMessage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalEventPublisher;
import reactor.core.publisher.Mono;

/**
 * 현재 리액티브 트랜잭션에 묶어 이벤트를 발행한다. 커밋 후({@code AFTER_COMMIT}) {@link PaymentEventMessageSender}가 받아 Kafka로 보낸다.
 * Spring 에 의존하는 발행 수단이라 domain 이 아니라 stream 어댑터에 둔다.
 */
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
