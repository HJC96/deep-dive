package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.common.UseCase;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentRecoveryUseCase;
import dev.deepdive.paymentsystem.payment.application.port.out.LoadPendingPaymentPort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentExecutorPort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdatePort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentValidationPort;
import dev.deepdive.paymentsystem.payment.domain.PaymentConfirmationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * UNKNOWN 이거나 3분 넘게 EXECUTING 에 머문 결제를 다시 승인 시도한다. 주기 실행은 {@code PaymentScheduler} 가 맡는다.
 * 재시도 전에 EXECUTING 으로 선점(조건부 UPDATE)하므로, 사용자의 재승인 요청이나 다른 인스턴스의 복구와 겹쳐도 PSP 를 중복 호출하지 않는다.
 */
@UseCase
public class PaymentRecoveryService implements PaymentRecoveryUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentRecoveryService.class);
    private static final int CONCURRENCY = 2;

    private final LoadPendingPaymentPort loadPendingPaymentPort;
    private final PaymentValidationPort paymentValidationPort;
    private final PaymentExecutorPort paymentExecutorPort;
    private final PaymentStatusUpdatePort paymentStatusUpdatePort;
    private final PaymentErrorHandler paymentErrorHandler;

    public PaymentRecoveryService(
            LoadPendingPaymentPort loadPendingPaymentPort,
            PaymentValidationPort paymentValidationPort,
            PaymentExecutorPort paymentExecutorPort,
            PaymentStatusUpdatePort paymentStatusUpdatePort,
            PaymentErrorHandler paymentErrorHandler
    ) {
        this.loadPendingPaymentPort = loadPendingPaymentPort;
        this.paymentValidationPort = paymentValidationPort;
        this.paymentExecutorPort = paymentExecutorPort;
        this.paymentStatusUpdatePort = paymentStatusUpdatePort;
        this.paymentErrorHandler = paymentErrorHandler;
    }

    @Override
    public Mono<Void> recovery() {
        return loadPendingPaymentPort.getPendingPayments()
                .map(pendingPaymentEvent -> new PaymentConfirmCommand(
                        pendingPaymentEvent.paymentKey(),
                        pendingPaymentEvent.orderId(),
                        pendingPaymentEvent.totalAmount()
                ))
                .flatMap(this::recover, CONCURRENCY)
                .then();
    }

    private Mono<PaymentConfirmationResult> recover(PaymentConfirmCommand command) {
        return paymentStatusUpdatePort.updatePaymentStatusToExecutingForRecovery(command.orderId())
                // 다른 요청이 먼저 선점했거나 이미 끝난 결제는 건너뛴다.
                .filter(claimed -> claimed)
                .flatMap(claimed -> paymentValidationPort.isValid(command.orderId(), command.amount()))
                .flatMap(valid -> paymentExecutorPort.execute(command))
                .flatMap(result -> paymentStatusUpdatePort.updatePaymentStatus(new PaymentStatusUpdateCommand(result))
                        .thenReturn(new PaymentConfirmationResult(result.paymentStatus(), result.failure())))
                .onErrorResume(error -> paymentErrorHandler.handlePaymentConfirmationError(error, command))
                // 한 건의 기록 실패가 회차 전체를 멈추지 않게 한다. 남은 건은 다음 회차에 다시 조회된다.
                .onErrorResume(error -> {
                    log.error("recovery - failed to recover payment. orderId={}", command.orderId(), error);
                    return Mono.empty();
                });
    }
}
