package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.payment.application.exception.PSPConfirmationException;
import dev.deepdive.paymentsystem.payment.application.exception.PaymentAlreadyProcessedException;
import dev.deepdive.paymentsystem.payment.application.exception.PaymentNotFoundException;
import dev.deepdive.paymentsystem.payment.application.exception.PaymentValidationException;
import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdatePort;
import dev.deepdive.paymentsystem.payment.domain.PaymentConfirmationResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentFailure;
import dev.deepdive.paymentsystem.payment.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PaymentErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentErrorHandler.class);

    private final PaymentStatusUpdatePort paymentStatusUpdatePort;

    public PaymentErrorHandler(PaymentStatusUpdatePort paymentStatusUpdatePort) {
        this.paymentStatusUpdatePort = paymentStatusUpdatePort;
    }

    public Mono<PaymentConfirmationResult> handlePaymentConfirmationError(Throwable error, PaymentConfirmCommand command) {
        // 이미 끝났거나 다른 요청이 처리 중인 결제: 상태를 건드리지 않고 현재 상태를 그대로 알려준다.
        if (error instanceof PaymentAlreadyProcessedException alreadyProcessed) {
            return Mono.just(alreadyProcessedResult(alreadyProcessed));
        }
        // 주문이 없으면 기록할 행도 없다. DB 에 쓰지 않고 FAILURE 로 응답한다.
        if (error instanceof PaymentNotFoundException) {
            return Mono.just(new PaymentConfirmationResult(
                    PaymentStatus.FAILURE,
                    new PaymentFailure(PaymentNotFoundException.ERROR_CODE, messageOf(error))
            ));
        }

        PaymentStatus status;
        PaymentFailure failure;

        if (error instanceof PSPConfirmationException pspError) {
            status = pspError.paymentStatus();
            failure = new PaymentFailure(pspError.errorCode(), pspError.errorMessage());
            if (status == PaymentStatus.SUCCESS) {
                // 성공인데 승인 상세(extraDetails)가 없으면 SUCCESS 를 기록할 수 없다(아웃박스·정산에 필요한 정보가 없다).
                // PSP 어댑터가 ALREADY_PROCESSED_PAYMENT 를 결제 조회로 풀어 정상 결과로 돌려주므로 여기까지 오면 안 되지만,
                // 오더라도 예외로 터뜨리지 않고 UNKNOWN 으로 남겨 복구 스케줄러가 다시 확인하게 한다.
                log.warn("PSP 가 성공을 알렸지만 승인 상세가 없어 UNKNOWN 으로 기록한다. orderId={}, code={}",
                        command.orderId(), pspError.errorCode());
                status = PaymentStatus.UNKNOWN;
            }
        } else if (error instanceof PaymentValidationException) {
            status = PaymentStatus.FAILURE;
            failure = new PaymentFailure(error.getClass().getSimpleName(), messageOf(error));
        } else {
            // 타임아웃을 포함한 나머지는 PSP 에서 실제로 어떻게 됐는지 모르므로 UNKNOWN. 복구 스케줄러가 다시 확인한다.
            status = PaymentStatus.UNKNOWN;
            failure = new PaymentFailure(error.getClass().getSimpleName(), messageOf(error));
        }

        PaymentStatusUpdateCommand paymentStatusUpdateCommand = new PaymentStatusUpdateCommand(
                command.paymentKey(),
                command.orderId(),
                status,
                null,
                failure
        );

        PaymentStatus finalStatus = status;
        PaymentFailure finalFailure = failure;
        return paymentStatusUpdatePort.updatePaymentStatus(paymentStatusUpdateCommand)
                .map(it -> new PaymentConfirmationResult(finalStatus, finalFailure))
                // 그 사이 다른 요청(복구 등)이 최종 상태를 먼저 기록했다면 그 상태를 따른다.
                .onErrorResume(PaymentAlreadyProcessedException.class, e -> Mono.just(alreadyProcessedResult(e)));
    }

    private PaymentConfirmationResult alreadyProcessedResult(PaymentAlreadyProcessedException error) {
        return new PaymentConfirmationResult(
                error.status(),
                new PaymentFailure(error.getClass().getSimpleName(), messageOf(error))
        );
    }

    private String messageOf(Throwable error) {
        return error.getMessage() == null ? "" : error.getMessage();
    }
}
