package dev.deepdive.spring.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AsyncVsExecutorServiceTest {

    @Autowired
    private AsyncWorkService asyncWorkService;

    @Test
    void Async_어노테이션은_Spring_TaskExecutor_스레드에서_실행된다() throws Exception {
        String callerThreadName = Thread.currentThread().getName();
        Order order = new Order("order-1", "han@example.com", "키보드", 120_000);

        EmailReceipt receipt = asyncWorkService
                .sendOrderConfirmationEmailWithAsync(order)
                .get(1, TimeUnit.SECONDS);

        assertThat(receipt.orderId()).isEqualTo("order-1");
        assertThat(receipt.to()).isEqualTo("han@example.com");
        assertThat(receipt.subject()).isEqualTo("[deep-dive] 주문 확인: order-1");
        assertThat(receipt.body()).isEqualTo("키보드 주문이 완료되었습니다. 결제 금액: 120000원");
        assertThat(receipt.runner()).isEqualTo("@Async");
        assertThat(receipt.threadName()).startsWith("spring-async-");
        assertThat(receipt.threadName()).isNotEqualTo(callerThreadName);
    }

    @Test
    void ExecutorService는_직접_등록한_ExecutorService_스레드에서_실행된다() throws Exception {
        String callerThreadName = Thread.currentThread().getName();
        Order order = new Order("order-2", "kim@example.com", "모니터", 300_000);

        EmailReceipt receipt = asyncWorkService
                .sendOrderConfirmationEmailWithExecutorService(order)
                .get(1, TimeUnit.SECONDS);

        assertThat(receipt.orderId()).isEqualTo("order-2");
        assertThat(receipt.to()).isEqualTo("kim@example.com");
        assertThat(receipt.subject()).isEqualTo("[deep-dive] 주문 확인: order-2");
        assertThat(receipt.body()).isEqualTo("모니터 주문이 완료되었습니다. 결제 금액: 300000원");
        assertThat(receipt.runner()).isEqualTo("ExecutorService");
        assertThat(receipt.threadName()).startsWith("java-executor-");
        assertThat(receipt.threadName()).isNotEqualTo(callerThreadName);
    }

    @Test
    void 같은_빈_내부에서_Async_메서드를_호출하면_프록시를_거치지_않아_동기_실행된다() throws Exception {
        String callerThreadName = Thread.currentThread().getName();
        Order order = new Order("order-3", "lee@example.com", "마우스", 50_000);

        EmailReceipt receipt = asyncWorkService
                .callAsyncMethodFromSameBean(order)
                .get(1, TimeUnit.SECONDS);

        assertThat(receipt.orderId()).isEqualTo("order-3");
        assertThat(receipt.to()).isEqualTo("lee@example.com");
        assertThat(receipt.subject()).isEqualTo("[deep-dive] 주문 확인: order-3");
        assertThat(receipt.body()).isEqualTo("마우스 주문이 완료되었습니다. 결제 금액: 50000원");
        assertThat(receipt.runner()).isEqualTo("@Async");
        assertThat(receipt.threadName()).isEqualTo(callerThreadName); // 내부 호출이라 테스트 스레드명 표출
        assertThat(receipt.threadName()).doesNotStartWith("spring-async-");
    }
}
