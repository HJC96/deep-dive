package dev.deepdive.spring.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "payment.retry.max-attempts=3",
        "payment.retry.initial-delay=10",
        "payment.retry.max-delay=40",
        "payment.retry.multiplier=2.0",
        "payment.retry.random=false"
})
@Import(RetryableTest.RetryTestConfiguration.class)
class RetryableTest {

    @Autowired
    private PaymentRetryService paymentRetryService;

    @Autowired
    private RecordingSleeper recordingSleeper;

    @MockitoBean
    private PaymentGatewayClient paymentGatewayClient;

    @BeforeEach
    void resetSleeper() {
        recordingSleeper.reset();
    }

    @Test
    void 첫_호출이_성공하면_RetryContext_0에서_종료한다() {
        PaymentRequest request = request("order-1");
        List<Integer> retryCounts = new ArrayList<>();
        when(paymentGatewayClient.requestPayment(request)).thenAnswer(invocation -> {
            retryCounts.add(currentRetryCount());
            return PaymentResult.completed(request.orderId());
        });

        PaymentResult result = paymentRetryService.requestPayment(request);

        assertThat(result).isEqualTo(PaymentResult.completed("order-1"));
        assertThat(retryCounts).containsExactly(0);
        assertThat(recordingSleeper.delays()).isEmpty();
        verify(paymentGatewayClient, times(1)).requestPayment(request);
    }

    @Test
    void 일시적_오류는_RetryContext를_증가시키며_세_번째_시도에서_성공한다() {
        PaymentRequest request = request("order-2");
        List<Integer> retryCounts = new ArrayList<>();
        when(paymentGatewayClient.requestPayment(request)).thenAnswer(invocation -> {
            int retryCount = currentRetryCount();
            retryCounts.add(retryCount);
            if (retryCount < 2) {
                throw new TemporaryPaymentException("일시적 오류: " + retryCount);
            }
            return PaymentResult.completed(request.orderId());
        });

        PaymentResult result = paymentRetryService.requestPayment(request);

        assertThat(result).isEqualTo(PaymentResult.completed("order-2"));
        assertThat(retryCounts).containsExactly(0, 1, 2);
        assertThat(recordingSleeper.delays()).containsExactly(10L, 20L);

        ArgumentCaptor<PaymentRequest> requestCaptor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentGatewayClient, times(3)).requestPayment(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .extracting(PaymentRequest::idempotencyKey)
                .containsOnly("payment-order-2");
    }

    @Test
    void 일시적_오류가_세_번_발생하면_복구_결과를_반환한다() {
        PaymentRequest request = request("order-3");
        List<Integer> retryCounts = new ArrayList<>();
        when(paymentGatewayClient.requestPayment(request)).thenAnswer(invocation -> {
            int retryCount = currentRetryCount();
            retryCounts.add(retryCount);
            throw new TemporaryPaymentException("일시적 오류: " + retryCount);
        });

        PaymentResult result = paymentRetryService.requestPayment(request);

        assertThat(result).isEqualTo(PaymentResult.reviewRequired("order-3"));
        assertThat(retryCounts).containsExactly(0, 1, 2);
        assertThat(recordingSleeper.delays()).containsExactly(10L, 20L);
        verify(paymentGatewayClient, times(3)).requestPayment(request);
    }

    @Test
    void 잘못된_요청은_재시도하지_않지만_Recover로_거절_결과를_만든다() {
        PaymentRequest request = request("order-4");
        List<Integer> retryCounts = new ArrayList<>();
        when(paymentGatewayClient.requestPayment(request)).thenAnswer(invocation -> {
            retryCounts.add(currentRetryCount());
            throw new InvalidPaymentRequestException("결제 정보 오류");
        });

        PaymentResult result = paymentRetryService.requestPayment(request);

        assertThat(result).isEqualTo(PaymentResult.rejected("order-4"));
        assertThat(retryCounts).containsExactly(0);
        assertThat(recordingSleeper.delays()).isEmpty();
        verify(paymentGatewayClient, times(1)).requestPayment(request);
    }

    @Test
    void 상태를_알_수_없는_오류는_재시도하지만_Recover하지_않는다() {
        PaymentRequest request = request("order-5");
        UnknownPaymentStateException exception = new UnknownPaymentStateException("결제 상태 확인 불가");
        List<Integer> retryCounts = new ArrayList<>();
        when(paymentGatewayClient.requestPayment(request)).thenAnswer(invocation -> {
            retryCounts.add(currentRetryCount());
            throw exception;
        });

        assertThatThrownBy(() -> paymentRetryService.requestPayment(request))
                .isSameAs(exception);
        assertThat(retryCounts).containsExactly(0, 1, 2);
        assertThat(recordingSleeper.delays()).containsExactly(10L, 20L);
        verify(paymentGatewayClient, times(3)).requestPayment(request);
    }

    private static PaymentRequest request(String orderId) {
        return new PaymentRequest(orderId, "payment-" + orderId);
    }

    private static int currentRetryCount() {
        RetryContext retryContext = RetrySynchronizationManager.getContext();
        assertThat(retryContext)
                .as("호출은 Spring Retry 프록시가 만든 RetryContext 안에서 실행되어야 한다")
                .isNotNull();
        return retryContext.getRetryCount();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RetryTestConfiguration {

        @Bean
        RecordingSleeper recordingSleeper() {
            return new RecordingSleeper();
        }
    }

    static class RecordingSleeper implements Sleeper {

        private final List<Long> delays = new ArrayList<>();

        @Override
        public void sleep(long backOffPeriod) {
            delays.add(backOffPeriod);
        }

        List<Long> delays() {
            return List.copyOf(delays);
        }

        void reset() {
            delays.clear();
        }
    }
}
