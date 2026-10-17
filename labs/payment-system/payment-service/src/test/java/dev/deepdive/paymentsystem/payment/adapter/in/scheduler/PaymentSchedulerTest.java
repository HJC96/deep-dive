package dev.deepdive.paymentsystem.payment.adapter.in.scheduler;

import dev.deepdive.paymentsystem.payment.application.port.in.PaymentEventMessageRelayUseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentRecoveryUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 예전에는 {@code @Profile("dev")} + {@code @EnableScheduling} 부재로 복구·릴레이가 한 번도 돌지 않았다.
 * 지금은 프로파일 없이 기본으로 돌고, payment.scheduler.enabled=false 로만 끌 수 있다.
 */
class PaymentSchedulerTest {

    private final ApplicationContextRunner contextRunner = runner(UseCases.class);

    private static ApplicationContextRunner runner(Class<?> useCases) {
        return new ApplicationContextRunner()
                .withUserConfiguration(useCases, PaymentScheduler.class)
                .withPropertyValues(
                        "payment.scheduler.recovery.initial-delay=PT0S",
                        "payment.scheduler.recovery.fixed-delay=PT0.05S",
                        "payment.scheduler.relay.initial-delay=PT0S",
                        "payment.scheduler.relay.fixed-delay=PT0.05S"
                );
    }

    @Test
    void should_run_recovery_and_relay_periodically_by_default_without_any_profile() {
        contextRunner.run(context -> {
            UseCases useCases = context.getBean(UseCases.class);
            assertThat(context).hasSingleBean(PaymentScheduler.class);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(useCases.recoveryCount.get()).isGreaterThanOrEqualTo(2);
                assertThat(useCases.relayCount.get()).isGreaterThanOrEqualTo(2);
            });
        });
    }

    @Test
    void should_not_schedule_anything_when_disabled() {
        contextRunner.withPropertyValues("payment.scheduler.enabled=false").run(context -> {
            UseCases useCases = context.getBean(UseCases.class);
            assertThat(context).doesNotHaveBean(PaymentScheduler.class);
            Thread.sleep(300);
            assertThat(useCases.recoveryCount.get()).isZero();
            assertThat(useCases.relayCount.get()).isZero();
        });
    }

    @Test
    void a_failing_round_does_not_stop_the_next_rounds() {
        runner(FailingUseCases.class).run(context -> {
            FailingUseCases useCases = context.getBean(FailingUseCases.class);
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(useCases.recoveryCount.get()).isGreaterThanOrEqualTo(2));
        });
    }

    @Configuration
    static class UseCases {
        final AtomicInteger recoveryCount = new AtomicInteger();
        final AtomicInteger relayCount = new AtomicInteger();

        @Bean
        PaymentRecoveryUseCase paymentRecoveryUseCase() {
            return () -> Mono.fromRunnable(recoveryCount::incrementAndGet);
        }

        @Bean
        PaymentEventMessageRelayUseCase paymentEventMessageRelayUseCase() {
            return () -> Mono.fromRunnable(relayCount::incrementAndGet);
        }
    }

    @Configuration
    static class FailingUseCases {
        final AtomicInteger recoveryCount = new AtomicInteger();

        @Bean
        PaymentRecoveryUseCase paymentRecoveryUseCase() {
            return () -> Mono.defer(() -> {
                recoveryCount.incrementAndGet();
                return Mono.error(new IllegalStateException("boom"));
            });
        }

        @Bean
        PaymentEventMessageRelayUseCase paymentEventMessageRelayUseCase() {
            return Mono::empty;
        }
    }
}
