package dev.deepdive.paymentsystem.payment.adapter.out.persistent;

import dev.deepdive.paymentsystem.common.PersistentAdapter;
import dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository.PaymentRepository;
import dev.deepdive.paymentsystem.payment.application.port.out.SavePaymentPort;
import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import reactor.core.publisher.Mono;

@PersistentAdapter
public class PaymentPersistentAdapter implements SavePaymentPort {

    private final PaymentRepository paymentRepository;

    public PaymentPersistentAdapter(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public Mono<Void> save(PaymentEvent paymentEvent) {
        return paymentRepository.save(paymentEvent);
    }
}
