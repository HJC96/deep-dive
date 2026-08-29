package dev.deepdive.paymentsystem.payment.adapter.out.web.toss.executor;

import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.response.TossPaymentConfirmationResponse;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.domain.PSPConfirmationStatus;
import dev.deepdive.paymentsystem.payment.domain.PaymentExecutionResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentExtraDetails;
import dev.deepdive.paymentsystem.payment.domain.PaymentMethod;
import dev.deepdive.paymentsystem.payment.domain.PaymentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class TossPaymentExecutor implements PaymentExecutor {

    private final WebClient tossPaymentWebClient;
    private final String uri;

    @Autowired
    public TossPaymentExecutor(WebClient tossPaymentWebClient) {
        this(tossPaymentWebClient, "/v1/payments/confirm");
    }

    public TossPaymentExecutor(WebClient tossPaymentWebClient, String uri) {
        this.tossPaymentWebClient = tossPaymentWebClient;
        this.uri = uri;
    }

    @Override
    public Mono<PaymentExecutionResult> execute(PaymentConfirmCommand command) {
        return tossPaymentWebClient.post()
                .uri(uri)
                .header("Idempotency-Key", command.orderId())
                .bodyValue("""
                        {
                          "paymentKey": "%s",
                          "orderId": "%s",
                          "amount": %d
                        }
                        """.formatted(command.paymentKey(), command.orderId(), command.amount()))
                .retrieve()
                .bodyToMono(TossPaymentConfirmationResponse.class)
                .map(it -> new PaymentExecutionResult(
                        command.paymentKey(),
                        command.orderId(),
                        new PaymentExtraDetails(
                                PaymentType.get(it.type()),
                                PaymentMethod.get(it.method()),
                                LocalDateTime.parse(it.approvedAt(), DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                it.orderName(),
                                PSPConfirmationStatus.get(it.status()),
                                it.totalAmount(),
                                it.toString()
                        ),
                        null,
                        true,
                        false,
                        false,
                        false
                ));
    }
}
