package dev.deepdive.paymentsystem.payment.adapter.in.stream;

import dev.deepdive.paymentsystem.common.StreamAdapter;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentCompleteUseCase;
import dev.deepdive.paymentsystem.payment.domain.WalletEventMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverOffset;

import java.util.function.Function;

@Configuration
@StreamAdapter
public class WalletEventMessageHandler {

    private final PaymentCompleteUseCase paymentCompleteUseCase;

    public WalletEventMessageHandler(PaymentCompleteUseCase paymentCompleteUseCase) {
        this.paymentCompleteUseCase = paymentCompleteUseCase;
    }

    @Bean
    public Function<Flux<Message<WalletEventMessage>>, Mono<Void>> wallet() {
        return flux -> flux.flatMap(message ->
                paymentCompleteUseCase.completePayment(message.getPayload())
                        .then(Mono.defer(() ->
                                message.getHeaders()
                                        .get(KafkaHeaders.ACKNOWLEDGMENT, ReceiverOffset.class)
                                        .commit()))
        ).then();
    }
}
