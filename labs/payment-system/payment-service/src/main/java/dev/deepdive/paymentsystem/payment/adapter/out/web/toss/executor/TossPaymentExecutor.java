package dev.deepdive.paymentsystem.payment.adapter.out.web.toss.executor;

import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.exception.PSPConfirmationException;
import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.exception.TossPaymentError;
import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.response.TossFailureResponse;
import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.response.TossPaymentConfirmationResponse;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.domain.PSPConfirmationStatus;
import dev.deepdive.paymentsystem.payment.domain.PaymentExecutionResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentExtraDetails;
import dev.deepdive.paymentsystem.payment.domain.PaymentMethod;
import dev.deepdive.paymentsystem.payment.domain.PaymentType;
import io.netty.handler.timeout.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
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
                .onStatus(
                        (HttpStatusCode statusCode) -> statusCode.is4xxClientError() || statusCode.is5xxServerError(),
                        response -> response.bodyToMono(TossFailureResponse.class)
                                .flatMap(it -> {
                                    TossPaymentError error = TossPaymentError.get(it.code());
                                    return Mono.error(new PSPConfirmationException(
                                            error.name(),
                                            error.description(),
                                            error.isSuccess(),
                                            error.isFailure(),
                                            error.isUnknown(),
                                            error.isRetryableError()
                                    ));
                                })
                )
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
                ))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)).jitter(0.1)
                        .filter(error -> (error instanceof PSPConfirmationException e && e.isRetryableError())
                                || error instanceof TimeoutException)
                        .onRetryExhaustedThrow((spec, retrySignal) -> retrySignal.failure()));
    }
}
