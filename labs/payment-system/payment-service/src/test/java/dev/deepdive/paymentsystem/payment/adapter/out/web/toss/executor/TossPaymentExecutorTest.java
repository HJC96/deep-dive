package dev.deepdive.paymentsystem.payment.adapter.out.web.toss.executor;

import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.exception.TossPaymentError;
import dev.deepdive.paymentsystem.payment.application.exception.PSPConfirmationException;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.test.PSPTestWebClientConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Toss 테스트 서버를 호출한다. 네트워크가 필요하므로 기본 실행에서는 빠진다(-PincludeIntegration).
 * Spring 컨텍스트 없이 Toss 문서의 공개 테스트 키로 WebClient 를 만든다.
 */
@Tag("ExternalIntegration")
class TossPaymentExecutorTest {

    private final PSPTestWebClientConfiguration pspTestWebClientConfiguration = new PSPTestWebClientConfiguration(
            System.getenv().getOrDefault("TOSS_BASE_URL", "https://api.tosspayments.com"),
            System.getenv().getOrDefault("TOSS_SECRET_KEY", "test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6")
    );

    @Test
    void should_handle_correctly_various_TossPaymentError_scenarios() {
        Arrays.stream(TossPaymentError.values()).forEach(error -> {
            PaymentConfirmCommand command = new PaymentConfirmCommand(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    10000L
            );

            TossPaymentExecutor paymentExecutor = new TossPaymentExecutor(
                    pspTestWebClientConfiguration.createTestTossWebClient(
                            Map.of("TossPayments-Test-Code", error.name())
                    ),
                    "/v1/payments/key-in"
            );

            try {
                paymentExecutor.execute(command).block();
            } catch (PSPConfirmationException e) {
                if (error == TossPaymentError.ALREADY_PROCESSED_PAYMENT) {
                    // 이미 처리됨은 결제 조회로 확인한다. 테스트 코드 헤더가 조회에도 붙어 조회가 실패하므로
                    // 성공으로 단정하지 않고 UNKNOWN 으로 남아야 한다.
                    assertThat(e.isUnknown()).isTrue();
                    return;
                }
                assertThat(e.isSuccess()).isEqualTo(error.isSuccess());
                assertThat(e.isFailure()).isEqualTo(error.isFailure());
                assertThat(e.isUnknown()).isEqualTo(error.isUnknown());
            }
        });
    }
}
