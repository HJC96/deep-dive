package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.common.UseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentEventMessageRelayUseCase;
import dev.deepdive.paymentsystem.payment.application.port.out.DispatchEventMessagePort;
import dev.deepdive.paymentsystem.payment.application.port.out.LoadPendingPaymentEventMessagePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.TimeUnit;

@UseCase
@Profile("dev")
public class PaymentEventMessageRelayService implements PaymentEventMessageRelayUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventMessageRelayService.class);

    private final LoadPendingPaymentEventMessagePort loadPendingPaymentEventMessagePort;
    private final DispatchEventMessagePort dispatchEventMessagePort;

    private final Scheduler scheduler = Schedulers.newSingle("message-relay");

    public PaymentEventMessageRelayService(
            LoadPendingPaymentEventMessagePort loadPendingPaymentEventMessagePort,
            DispatchEventMessagePort dispatchEventMessagePort
    ) {
        this.loadPendingPaymentEventMessagePort = loadPendingPaymentEventMessagePort;
        this.dispatchEventMessagePort = dispatchEventMessagePort;
    }

    @Scheduled(fixedDelay = 180, initialDelay = 180, timeUnit = TimeUnit.SECONDS)
    @Override
    public void relay() {
        loadPendingPaymentEventMessagePort.getPendingPaymentEventMessage()
                .doOnNext(dispatchEventMessagePort::dispatch)
                .onErrorContinue((err, obj) -> log.error("messageRelay - failed to relay message.", err))
                .subscribeOn(scheduler)
                .subscribe();
    }
}
