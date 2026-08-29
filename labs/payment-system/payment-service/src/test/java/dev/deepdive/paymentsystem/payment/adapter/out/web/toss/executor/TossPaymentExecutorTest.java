package dev.deepdive.paymentsystem.payment.adapter.out.web.toss.executor;

import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.exception.PSPConfirmationException;
import dev.deepdive.paymentsystem.payment.adapter.out.web.toss.exception.TossPaymentError;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.test.PSPTestWebClientConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PSPTestWebClientConfiguration.class)
@Tag("ExternalIntegration")
class TossPaymentExecutorTest {

    @Autowired
    private PSPTestWebClientConfiguration pspTestWebClientConfiguration;

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
                assertThat(e.isSuccess()).isEqualTo(error.isSuccess());
                assertThat(e.isFailure()).isEqualTo(error.isFailure());
                assertThat(e.isUnknown()).isEqualTo(error.isUnknown());
            }
        });
    }
}
