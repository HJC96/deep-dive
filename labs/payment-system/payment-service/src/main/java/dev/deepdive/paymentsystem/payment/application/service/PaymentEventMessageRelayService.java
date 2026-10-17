package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.common.UseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentEventMessageRelayUseCase;
import dev.deepdive.paymentsystem.payment.application.port.out.DispatchEventMessagePort;
import dev.deepdive.paymentsystem.payment.application.port.out.LoadPendingPaymentEventMessagePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 아웃박스에 남아 있는(INIT/FAILURE) 메시지를 다시 발행한다. 주기 실행은 {@code PaymentScheduler} 가 맡는다.
 */
@UseCase
public class PaymentEventMessageRelayService implements PaymentEventMessageRelayUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventMessageRelayService.class);

    private final LoadPendingPaymentEventMessagePort loadPendingPaymentEventMessagePort;
    private final DispatchEventMessagePort dispatchEventMessagePort;

    public PaymentEventMessageRelayService(
            LoadPendingPaymentEventMessagePort loadPendingPaymentEventMessagePort,
            DispatchEventMessagePort dispatchEventMessagePort
    ) {
        this.loadPendingPaymentEventMessagePort = loadPendingPaymentEventMessagePort;
        this.dispatchEventMessagePort = dispatchEventMessagePort;
    }

    @Override
    public Mono<Void> relay() {
        return loadPendingPaymentEventMessagePort.getPendingPaymentEventMessage()
                .concatMap(message -> Mono.fromRunnable(() -> dispatchEventMessagePort.dispatch(message))
                        // 한 건이 실패해도 나머지는 계속 보낸다. 실패한 건은 아웃박스에 남아 다음 회차에 다시 시도된다.
                        .onErrorResume(error -> {
                            log.error("messageRelay - failed to relay message. orderId={}", message.orderId(), error);
                            return Mono.empty();
                        }))
                .then();
    }
}
