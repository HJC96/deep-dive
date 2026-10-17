package dev.deepdive.paymentsystem.payment.adapter.out.web.toss.executor;

import dev.deepdive.paymentsystem.payment.application.exception.PSPConfirmationException;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.domain.PSPConfirmationStatus;
import dev.deepdive.paymentsystem.payment.domain.PaymentExecutionResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentMethod;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import dev.deepdive.paymentsystem.payment.domain.PaymentType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 네트워크 없이 WebClient 의 ExchangeFunction 을 바꿔 끼워 Toss 응답을 흉내 낸다.
 */
class TossPaymentExecutorUnitTest {

    private static final PaymentConfirmCommand COMMAND = new PaymentConfirmCommand("pk_1", "order_1", 15000L);

    private final List<ClientRequest> requests = new ArrayList<>();

    @Test
    void should_map_a_successful_confirmation() {
        TossPaymentExecutor executor = executor(request -> json(HttpStatus.OK, payment("order_1", 15000, "DONE", "카드", "NORMAL")));

        PaymentExecutionResult result = executor.execute(COMMAND).block();

        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.extraDetails().method()).isEqualTo(PaymentMethod.CARD);
        assertThat(result.extraDetails().type()).isEqualTo(PaymentType.NORMAL);
        assertThat(result.extraDetails().pspConfirmationStatus()).isEqualTo(PSPConfirmationStatus.DONE);
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo(HttpMethod.POST);
            assertThat(request.headers().getFirst("Idempotency-Key")).isEqualTo("order_1");
        });
    }

    @Test
    void should_not_fail_after_approval_when_PSP_returns_an_unknown_method_or_type() {
        // 돈은 이미 움직였다. 모르는 값 때문에 승인 결과를 버리면 안 된다.
        TossPaymentExecutor executor = executor(request -> json(HttpStatus.OK,
                payment("order_1", 15000, "DONE", "포인트", "SOMETHING_NEW")));

        PaymentExecutionResult result = executor.execute(COMMAND).block();

        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.extraDetails().method()).isEqualTo(PaymentMethod.UNKNOWN);
        assertThat(result.extraDetails().type()).isEqualTo(PaymentType.UNKNOWN);
    }

    @Test
    void should_look_up_the_payment_and_return_SUCCESS_with_details_when_already_processed() {
        TossPaymentExecutor executor = executor(request -> {
            if (request.method() == HttpMethod.POST) {
                return json(HttpStatus.BAD_REQUEST, failure("ALREADY_PROCESSED_PAYMENT"));
            }
            return json(HttpStatus.OK, payment("order_1", 15000, "DONE", "간편결제", "NORMAL"));
        });

        PaymentExecutionResult result = executor.execute(COMMAND).block();

        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.extraDetails()).isNotNull();
        assertThat(result.extraDetails().method()).isEqualTo(PaymentMethod.EASY_PAY);
        assertThat(result.extraDetails().totalAmount()).isEqualTo(15000L);
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).method()).isEqualTo(HttpMethod.GET);
        assertThat(requests.get(1).url().getPath()).isEqualTo("/v1/payments/pk_1");
        // 승인과 같은 시크릿 키 인증을 그대로 쓴다.
        assertThat(requests.get(1).headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Basic dGVzdDo=");
    }

    @Test
    void should_stay_UNKNOWN_when_the_lookup_of_an_already_processed_payment_fails() {
        TossPaymentExecutor executor = executor(request -> request.method() == HttpMethod.POST
                ? json(HttpStatus.BAD_REQUEST, failure("ALREADY_PROCESSED_PAYMENT"))
                : json(HttpStatus.INTERNAL_SERVER_ERROR, failure("FAILED_INTERNAL_SYSTEM_PROCESSING")));

        assertThatThrownBy(() -> executor.execute(COMMAND).block())
                .isInstanceOfSatisfying(PSPConfirmationException.class, e -> {
                    assertThat(e.paymentStatus()).isEqualTo(PaymentStatus.UNKNOWN);
                    assertThat(e.errorCode()).isEqualTo(TossPaymentExecutor.LOOKUP_FAILED);
                });
    }

    @Test
    void should_stay_UNKNOWN_when_the_looked_up_payment_does_not_match_the_request() {
        TossPaymentExecutor executor = executor(request -> request.method() == HttpMethod.POST
                ? json(HttpStatus.BAD_REQUEST, failure("ALREADY_PROCESSED_PAYMENT"))
                : json(HttpStatus.OK, payment("order_1", 9999, "CANCELED", "카드", "NORMAL")));

        assertThatThrownBy(() -> executor.execute(COMMAND).block())
                .isInstanceOfSatisfying(PSPConfirmationException.class, e -> {
                    assertThat(e.paymentStatus()).isEqualTo(PaymentStatus.UNKNOWN);
                    assertThat(e.errorCode()).isEqualTo(TossPaymentExecutor.LOOKUP_MISMATCH);
                });
    }

    @Test
    void should_classify_a_failure_code_as_FAILURE_without_retry() {
        TossPaymentExecutor executor = executor(request -> json(HttpStatus.FORBIDDEN, failure("REJECT_CARD_PAYMENT")));

        assertThatThrownBy(() -> executor.execute(COMMAND).block())
                .isInstanceOfSatisfying(PSPConfirmationException.class,
                        e -> assertThat(e.paymentStatus()).isEqualTo(PaymentStatus.FAILURE));
        assertThat(requests).hasSize(1);
    }

    @Test
    void should_retry_unknown_errors_and_end_UNKNOWN() {
        TossPaymentExecutor executor = executor(request -> json(HttpStatus.INTERNAL_SERVER_ERROR, failure("PROVIDER_ERROR")));

        assertThatThrownBy(() -> executor.execute(COMMAND).block())
                .isInstanceOfSatisfying(PSPConfirmationException.class,
                        e -> assertThat(e.paymentStatus()).isEqualTo(PaymentStatus.UNKNOWN));
        assertThat(requests).hasSize(3); // 최초 1회 + 재시도 2회
    }

    @Test
    void should_treat_an_error_without_body_as_UNKNOWN() {
        TossPaymentExecutor executor = executor(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY).build()));

        assertThatThrownBy(() -> executor.execute(COMMAND).block())
                .isInstanceOfSatisfying(PSPConfirmationException.class,
                        e -> assertThat(e.paymentStatus()).isEqualTo(PaymentStatus.UNKNOWN));
    }

    private TossPaymentExecutor executor(Function<ClientRequest, Mono<ClientResponse>> responder) {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://toss.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic dGVzdDo=")
                .exchangeFunction(request -> {
                    requests.add(request);
                    return responder.apply(request);
                })
                .build();
        return new TossPaymentExecutor(webClient, TossPaymentExecutor.CONFIRM_URI, Duration.ofMillis(1));
    }

    private static Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private static String failure(String code) {
        return """
                {"code": "%s", "message": "test"}
                """.formatted(code);
    }

    private static String payment(String orderId, long totalAmount, String status, String method, String type) {
        return """
                {
                  "paymentKey": "pk_1",
                  "type": "%s",
                  "orderId": "%s",
                  "orderName": "test_order",
                  "method": "%s",
                  "totalAmount": %d,
                  "status": "%s",
                  "approvedAt": "2024-01-01T10:00:00+09:00"
                }
                """.formatted(type, orderId, method, totalAmount, status);
    }
}
