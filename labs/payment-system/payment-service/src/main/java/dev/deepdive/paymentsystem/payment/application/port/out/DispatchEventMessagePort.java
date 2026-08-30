package dev.deepdive.paymentsystem.payment.application.port.out;

import dev.deepdive.paymentsystem.payment.domain.PaymentEventMessage;

public interface DispatchEventMessagePort {

    void dispatch(PaymentEventMessage paymentEventMessage);
}
