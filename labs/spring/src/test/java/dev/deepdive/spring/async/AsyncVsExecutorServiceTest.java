package dev.deepdive.spring.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AsyncVsExecutorServiceTest {

    @Autowired
    private AsyncWorkService asyncWorkService;

    @Autowired
    private ExecutorServiceWorkService executorServiceWorkService;

    @Test
    void asyncAnnotationRunsOnSpringTaskExecutor() throws Exception {
        String callerThreadName = Thread.currentThread().getName();

        CompletableFuture<WorkResult> future = asyncWorkService.doWork("async", Duration.ofMillis(200));

        assertThat(future.isDone()).isFalse();

        WorkResult result = future.get(2, TimeUnit.SECONDS);
        assertThat(result.threadName()).startsWith("spring-async-");
        assertThat(result.threadName()).isNotEqualTo(callerThreadName);
    }

    @Test
    void executorServiceRunsOnJavaExecutorService() throws Exception {
        String callerThreadName = Thread.currentThread().getName();

        CompletableFuture<WorkResult> future = executorServiceWorkService.doWork("executor-service", Duration.ofMillis(200));

        assertThat(future.isDone()).isFalse();

        WorkResult result = future.get(2, TimeUnit.SECONDS);
        assertThat(result.threadName()).startsWith("java-executor-");
        assertThat(result.threadName()).isNotEqualTo(callerThreadName);
    }

    @Test
    void asyncAnnotationIsNotAppliedWhenCalledInsideSameBean() throws Exception {
        String callerThreadName = Thread.currentThread().getName();

        CompletableFuture<WorkResult> future = asyncWorkService.callAsyncMethodFromSameBean("self-invocation");

        assertThat(future.isDone()).isTrue();

        WorkResult result = future.get(2, TimeUnit.SECONDS);
        assertThat(result.threadName()).isEqualTo(callerThreadName);
        assertThat(result.threadName()).doesNotStartWith("spring-async-");
    }
}
