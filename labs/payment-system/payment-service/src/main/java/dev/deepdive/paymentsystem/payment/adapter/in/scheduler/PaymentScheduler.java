package dev.deepdive.paymentsystem.payment.adapter.in.scheduler;

import dev.deepdive.paymentsystem.payment.application.port.in.PaymentEventMessageRelayUseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentRecoveryUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

/**
 * 결제 복구와 아웃박스 릴레이를 주기적으로 깨우는 인바운드 어댑터.
 * 기본으로 켜져 있고 {@code payment.scheduler.enabled=false} 로 끌 수 있다(테스트가 백그라운드 간섭 없이 직접 호출하도록).
 *
 * <p>스케줄러 스레드(리액터 non-blocking 스레드가 아님)에서 block 으로 회차가 끝날 때까지 기다린다.
 * 그래서 fixedDelay 가 "이전 회차가 끝난 뒤부터" 재는 간격이 되고, 회차가 겹쳐 같은 결제를 두 번 집어 드는 일이 없다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "payment.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentScheduler.class);
    private static final Duration MAX_ROUND_DURATION = Duration.ofMinutes(2);

    private final PaymentRecoveryUseCase paymentRecoveryUseCase;
    private final PaymentEventMessageRelayUseCase paymentEventMessageRelayUseCase;

    public PaymentScheduler(
            PaymentRecoveryUseCase paymentRecoveryUseCase,
            PaymentEventMessageRelayUseCase paymentEventMessageRelayUseCase
    ) {
        this.paymentRecoveryUseCase = paymentRecoveryUseCase;
        this.paymentEventMessageRelayUseCase = paymentEventMessageRelayUseCase;
    }

    @Scheduled(
            initialDelayString = "${payment.scheduler.recovery.initial-delay:PT3M}",
            fixedDelayString = "${payment.scheduler.recovery.fixed-delay:PT3M}"
    )
    public void recovery() {
        try {
            paymentRecoveryUseCase.recovery().block(MAX_ROUND_DURATION);
        } catch (RuntimeException e) {
            log.error("recovery - round failed.", e);
        }
    }

    @Scheduled(
            initialDelayString = "${payment.scheduler.relay.initial-delay:PT3M}",
            fixedDelayString = "${payment.scheduler.relay.fixed-delay:PT3M}"
    )
    public void relay() {
        try {
            paymentEventMessageRelayUseCase.relay().block(MAX_ROUND_DURATION);
        } catch (RuntimeException e) {
            log.error("messageRelay - round failed.", e);
        }
    }
}
