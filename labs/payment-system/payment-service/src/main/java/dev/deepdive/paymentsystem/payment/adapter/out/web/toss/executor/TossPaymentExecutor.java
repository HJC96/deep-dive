package dev.deepdive.paymentsystem.payment.adapter.out.web.toss.executor;

import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.exception.TossPaymentError;
import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.response.TossFailureResponse;
import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.response.TossPaymentConfirmationResponse;
import dev.deepdive.paymentsystem.payment.application.exception.PSPConfirmationException;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.domain.PSPConfirmationStatus;
import dev.deepdive.paymentsystem.payment.domain.PaymentExecutionResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentExtraDetails;
import dev.deepdive.paymentsystem.payment.domain.PaymentMethod;
import dev.deepdive.paymentsystem.payment.domain.PaymentType;
import io.netty.handler.timeout.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Component
public class TossPaymentExecutor implements PaymentExecutor {

    private static final Logger log = LoggerFactory.getLogger(TossPaymentExecutor.class);

    static final String CONFIRM_URI = "/v1/payments/confirm";
    // Toss "결제 조회" API. 승인과 같은 시크릿 키 Basic 인증을 쓴다.
    static final String LOOKUP_URI = "/v1/payments/{paymentKey}";

    static final String LOOKUP_FAILED = "ALREADY_PROCESSED_PAYMENT_LOOKUP_FAILED";
    static final String LOOKUP_MISMATCH = "ALREADY_PROCESSED_PAYMENT_LOOKUP_MISMATCH";

    private final WebClient tossPaymentWebClient;
    private final String uri;
    private final Duration retryBackoff;

    @Autowired
    public TossPaymentExecutor(WebClient tossPaymentWebClient) {
        this(tossPaymentWebClient, CONFIRM_URI);
    }

    public TossPaymentExecutor(WebClient tossPaymentWebClient, String uri) {
        this(tossPaymentWebClient, uri, Duration.ofSeconds(1));
    }

    TossPaymentExecutor(WebClient tossPaymentWebClient, String uri, Duration retryBackoff) {
        this.tossPaymentWebClient = tossPaymentWebClient;
        this.uri = uri;
        this.retryBackoff = retryBackoff;
    }

    @Override
    public Mono<PaymentExecutionResult> execute(PaymentConfirmCommand command) {
        return confirm(command)
                // ALREADY_PROCESSED_PAYMENT: 이 결제는 PSP 에서 이미 승인됐다(예: 응답을 못 받은 채 재시도한 경우).
                // 승인 응답이 없으니 결제를 조회해 같은 Payment 객체를 받아 정상 성공 결과로 바꾼다.
                .onErrorResume(TossPaymentExecutor::isAlreadyProcessed, error -> lookUpAlreadyProcessedPayment(command))
                .map(response -> toExecutionResult(command, response))
                .retryWhen(Retry.backoff(2, retryBackoff).jitter(0.1)
                        .filter(error -> (error instanceof PSPConfirmationException e && e.isRetryableError())
                                || error instanceof TimeoutException)
                        .onRetryExhaustedThrow((spec, retrySignal) -> retrySignal.failure()));
    }

    private Mono<TossPaymentConfirmationResponse> confirm(PaymentConfirmCommand command) {
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
                        TossPaymentExecutor::toPSPConfirmationException
                )
                .bodyToMono(TossPaymentConfirmationResponse.class);
    }

    /**
     * Toss 문서 기준으로 결제 조회(GET /v1/payments/{paymentKey}) 응답은 승인 응답과 같은 Payment 객체다(가정).
     * 그래서 같은 응답 레코드로 받는다. 조회 결과가 이 주문·금액의 완료(DONE) 결제임을 확인할 수 없으면
     * 성공으로 단정하지 않고 UNKNOWN 으로 남겨 복구 스케줄러가 다시 확인하게 한다.
     */
    private Mono<TossPaymentConfirmationResponse> lookUpAlreadyProcessedPayment(PaymentConfirmCommand command) {
        return tossPaymentWebClient.get()
                .uri(LOOKUP_URI, command.paymentKey())
                .retrieve()
                .onStatus(
                        (HttpStatusCode statusCode) -> statusCode.is4xxClientError() || statusCode.is5xxServerError(),
                        response -> response.bodyToMono(TossFailureResponse.class)
                                .map(it -> lookupFailed("결제 조회 실패 (code: " + it.code() + ", message: " + it.message() + ")"))
                                .defaultIfEmpty(lookupFailed("결제 조회 실패 (status: " + response.statusCode() + ")"))
                )
                .bodyToMono(TossPaymentConfirmationResponse.class)
                .switchIfEmpty(Mono.error(() -> lookupFailed("결제 조회 응답이 비어 있습니다.")))
                .onErrorMap(error -> !(error instanceof PSPConfirmationException) && !(error instanceof TimeoutException),
                        error -> lookupFailed("결제 조회 중 오류: " + error.getMessage()))
                .flatMap(response -> {
                    boolean matches = Objects.equals(response.orderId(), command.orderId())
                            && response.totalAmount() != null && response.totalAmount() == command.amount()
                            && PSPConfirmationStatus.DONE.name().equals(response.status());
                    if (!matches) {
                        return Mono.error(new PSPConfirmationException(
                                LOOKUP_MISMATCH,
                                "이미 처리된 결제의 조회 결과가 승인 요청과 맞지 않습니다. (orderId: %s, status: %s, totalAmount: %s)"
                                        .formatted(response.orderId(), response.status(), response.totalAmount()),
                                false, false, true, false));
                    }
                    log.info("ALREADY_PROCESSED_PAYMENT 를 결제 조회로 확인해 성공으로 처리한다. orderId={}", command.orderId());
                    return Mono.just(response);
                });
    }

    private PaymentExecutionResult toExecutionResult(PaymentConfirmCommand command, TossPaymentConfirmationResponse response) {
        PaymentType type = PaymentType.get(response.type());
        if (type == PaymentType.UNKNOWN) {
            log.warn("알 수 없는 결제 타입 (type: {}) 이라 UNKNOWN 으로 기록한다. orderId={}", response.type(), command.orderId());
        }
        PaymentMethod method = PaymentMethod.get(response.method());
        if (method == PaymentMethod.UNKNOWN) {
            log.warn("알 수 없는 결제 수단 (method: {}) 이라 UNKNOWN 으로 기록한다. orderId={}", response.method(), command.orderId());
        }

        return new PaymentExecutionResult(
                command.paymentKey(),
                command.orderId(),
                new PaymentExtraDetails(
                        type,
                        method,
                        response.approvedAt() == null
                                ? null
                                : LocalDateTime.parse(response.approvedAt(), DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        response.orderName(),
                        PSPConfirmationStatus.get(response.status()),
                        response.totalAmount() == null ? command.amount() : response.totalAmount(),
                        response.toString()
                ),
                null,
                true,
                false,
                false,
                false
        );
    }

    private static Mono<? extends Throwable> toPSPConfirmationException(ClientResponse response) {
        return response.bodyToMono(TossFailureResponse.class)
                .map(it -> TossPaymentError.get(it.code()))
                // 에러 본문이 없으면 결과를 알 수 없다. 빈 Mono 를 돌려주면 WebClient 가 정상 응답으로 처리해 버리므로 UNKNOWN 으로 바꾼다.
                .defaultIfEmpty(TossPaymentError.UNKNOWN)
                .flatMap(error -> Mono.error(new PSPConfirmationException(
                        error.name(),
                        error.description(),
                        error.isSuccess(),
                        error.isFailure(),
                        error.isUnknown(),
                        error.isRetryableError()
                )));
    }

    private static boolean isAlreadyProcessed(Throwable error) {
        return error instanceof PSPConfirmationException e
                && TossPaymentError.ALREADY_PROCESSED_PAYMENT.name().equals(e.errorCode());
    }

    private static PSPConfirmationException lookupFailed(String message) {
        // 조회가 안 되면 승인 여부를 확정할 수 없다. 재시도하지 않고 UNKNOWN 으로 남겨 복구에 맡긴다.
        return new PSPConfirmationException(LOOKUP_FAILED, message, false, false, true, false);
    }
}
