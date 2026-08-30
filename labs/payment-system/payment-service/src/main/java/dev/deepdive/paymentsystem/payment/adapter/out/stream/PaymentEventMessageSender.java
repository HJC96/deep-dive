package dev.deepdive.paymentsystem.payment.adapter.out.stream;

import dev.deepdive.paymentsystem.common.StreamAdapter;
import dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository.PaymentOutboxRepository;
import dev.deepdive.paymentsystem.payment.application.port.out.DispatchEventMessagePort;
import dev.deepdive.paymentsystem.payment.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.payment.domain.PaymentEventMessageType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.sender.SenderResult;

import java.util.function.Supplier;

@Configuration
@StreamAdapter
public class PaymentEventMessageSender implements DispatchEventMessagePort {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventMessageSender.class);

    private final PaymentOutboxRepository paymentOutboxRepository;

    private final Sinks.Many<Message<PaymentEventMessage>> sender =
            Sinks.many().unicast().onBackpressureBuffer();
    private final Sinks.Many<SenderResult<String>> sendResult =
            Sinks.many().unicast().onBackpressureBuffer();

    public PaymentEventMessageSender(PaymentOutboxRepository paymentOutboxRepository) {
        this.paymentOutboxRepository = paymentOutboxRepository;
    }

    @Bean
    public Supplier<Flux<Message<PaymentEventMessage>>> send() {
        return () -> sender.asFlux()
                .onErrorContinue((err, obj) ->
                        log.error("sendEventMessage - failed to send eventMessage", err));
    }

    @Bean(name = "payment-result")
    public FluxMessageChannel sendResultChannel() {
        return new FluxMessageChannel();
    }

    @ServiceActivator(inputChannel = "payment-result")
    public void receiveSendResult(SenderResult<String> results) {
        if (results.exception() != null) {
            log.error("sendEventMessage - receive an exception for event message send.", results.exception());
        }

        sendResult.emitNext(results, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    @PostConstruct
    public void handleSendResult() {
        sendResult.asFlux()
                .flatMap(it -> it.recordMetadata() != null
                        ? paymentOutboxRepository.markMessageAsSent(it.correlationMetadata(), PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS)
                        : paymentOutboxRepository.markMessageAsFailure(it.correlationMetadata(), PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS))
                .onErrorContinue((err, obj) ->
                        log.error("sendEventMessage - failed to mark the outbox message.", err))
                .subscribeOn(Schedulers.newSingle("handle-send-result-event-message"))
                .subscribe();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatchAfterCommit(PaymentEventMessage paymentEventMessage) {
        dispatch(paymentEventMessage);
    }

    @Override
    public void dispatch(PaymentEventMessage paymentEventMessage) {
        sender.emitNext(createEventMessage(paymentEventMessage), Sinks.EmitFailureHandler.FAIL_FAST);
    }

    private Message<PaymentEventMessage> createEventMessage(PaymentEventMessage paymentEventMessage) {
        return MessageBuilder.withPayload(paymentEventMessage)
                .setHeader(IntegrationMessageHeaderAccessor.CORRELATION_ID, paymentEventMessage.payload().get("orderId"))
                .setHeader(KafkaHeaders.PARTITION, paymentEventMessage.metadata().getOrDefault("partitionKey", 0))
                .build();
    }
}
