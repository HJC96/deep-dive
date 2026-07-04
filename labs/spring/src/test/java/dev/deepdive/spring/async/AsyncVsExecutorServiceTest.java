package dev.deepdive.spring.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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

    @Test
    void before_순차_작업은_세_작업_시간의_합에_가깝다() {
        Order order = new Order("order-4", "park@example.com", "노트북", 1_500_000);

        long beforeStartedAt = System.nanoTime();
        EmailReceipt before = asyncWorkService.prepareOrderConfirmationEmailSequentially(order);
        Duration beforeElapsed = Duration.ofNanos(System.nanoTime() - beforeStartedAt);

        assertThat(before.body())
                .isEqualTo("노트북 주문이 완료되었습니다. 결제 금액: 1500000원, 결제 승인, 재고 확보, 배송 예정: 2일 후 도착");
        assertThat(beforeElapsed).isGreaterThanOrEqualTo(Duration.ofMillis(600));
    }

    @Test
    void after_CompletableFuture_병렬_작업은_가장_느린_작업_시간에_가깝다() {
        Order order = new Order("order-4", "park@example.com", "노트북", 1_500_000);

        long afterStartedAt = System.nanoTime();
        EmailReceipt after = asyncWorkService.prepareOrderConfirmationEmailInParallel(order);
        Duration afterElapsed = Duration.ofNanos(System.nanoTime() - afterStartedAt);

        assertThat(after.body())
                .isEqualTo("노트북 주문이 완료되었습니다. 결제 금액: 1500000원, 결제 승인, 재고 확보, 배송 예정: 2일 후 도착");
        assertThat(afterElapsed).isLessThan(Duration.ofMillis(500));
    }

    @Test
    void before_Async_작업을_시작하자마자_기다리면_세_작업_시간의_합에_가깝다() {
        Order order = new Order("order-5", "choi@example.com", "스피커", 80_000);

        long beforeStartedAt = System.nanoTime();
        EmailReceipt before = asyncWorkService.prepareOrderConfirmationEmailWithAsyncSequentially(order);
        Duration beforeElapsed = Duration.ofNanos(System.nanoTime() - beforeStartedAt);

        assertThat(before.body())
                .isEqualTo("스피커 주문이 완료되었습니다. 결제 금액: 80000원, 결제 승인, 재고 확보, 배송 예정: 2일 후 도착");
        assertThat(beforeElapsed).isGreaterThanOrEqualTo(Duration.ofMillis(600));
    }

    @Test
    void after_Async_작업을_먼저_시작하고_마지막에_기다리면_가장_느린_작업_시간에_가깝다() {
        Order order = new Order("order-5", "choi@example.com", "스피커", 80_000);

        long afterStartedAt = System.nanoTime();
        EmailReceipt after = asyncWorkService.prepareOrderConfirmationEmailWithAsyncInParallel(order);
        Duration afterElapsed = Duration.ofNanos(System.nanoTime() - afterStartedAt);

        assertThat(after.body())
                .isEqualTo("스피커 주문이 완료되었습니다. 결제 금액: 80000원, 결제 승인, 재고 확보, 배송 예정: 2일 후 도착");
        assertThat(afterElapsed).isLessThan(Duration.ofMillis(500));
    }
}
