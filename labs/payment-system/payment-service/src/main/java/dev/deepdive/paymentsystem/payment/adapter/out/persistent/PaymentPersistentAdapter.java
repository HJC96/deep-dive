package dev.deepdive.paymentsystem.payment.adapter.out.persistent;

import dev.deepdive.paymentsystem.common.PersistentAdapter;
import dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository.PaymentRepository;
import dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository.PaymentStatusUpdateRepository;
import dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository.PaymentValidationRepository;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdatePort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentValidationPort;
import dev.deepdive.paymentsystem.payment.application.port.out.SavePaymentPort;
import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import reactor.core.publisher.Mono;

@PersistentAdapter
public class PaymentPersistentAdapter implements SavePaymentPort, PaymentStatusUpdatePort, PaymentValidationPort {

    private final PaymentRepository paymentRepository;
    private final PaymentStatusUpdateRepository paymentStatusUpdateRepository;
    private final PaymentValidationRepository paymentValidationRepository;

    public PaymentPersistentAdapter(
            PaymentRepository paymentRepository,
            PaymentStatusUpdateRepository paymentStatusUpdateRepository,
            PaymentValidationRepository paymentValidationRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentStatusUpdateRepository = paymentStatusUpdateRepository;
        this.paymentValidationRepository = paymentValidationRepository;
    }

    @Override
    public Mono<Void> save(PaymentEvent paymentEvent) {
        return paymentRepository.save(paymentEvent);
    }

    @Override
    public Mono<Boolean> updatePaymentStatusToExecuting(String orderId, String paymentKey) {
        return paymentStatusUpdateRepository.updatePaymentStatusToExecuting(orderId, paymentKey);
    }

    @Override
    public Mono<Boolean> updatePaymentStatus(PaymentStatusUpdateCommand command) {
        return paymentStatusUpdateRepository.updatePaymentStatus(command);
    }

    @Override
    public Mono<Boolean> isValid(String orderId, long amount) {
        return paymentValidationRepository.isValid(orderId, amount);
    }
}
