package dev.deepdive.paymentsystem.payment.test;

import dev.deepdive.paymentsystem.payment.application.port.in.PaymentConfirmCommand;
import dev.deepdive.paymentsystem.payment.domain.PSPConfirmationStatus;
import dev.deepdive.paymentsystem.payment.domain.PaymentExecutionResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentExtraDetails;
import dev.deepdive.paymentsystem.payment.domain.PaymentFailure;
import dev.deepdive.paymentsystem.payment.domain.PaymentMethod;
import dev.deepdive.paymentsystem.payment.domain.PaymentType;

import java.time.LocalDateTime;

public final class PaymentTestFixtures {

    private PaymentTestFixtures() {
    }

    public static PaymentExtraDetails extraDetails(long amount, PaymentMethod method) {
        return new PaymentExtraDetails(
                PaymentType.NORMAL, method, LocalDateTime.now(),
                "test_order_name", PSPConfirmationStatus.DONE, amount, "{}");
    }

    public static PaymentExecutionResult successResult(PaymentConfirmCommand command) {
        return successResult(command, PaymentMethod.EASY_PAY);
    }

    public static PaymentExecutionResult successResult(PaymentConfirmCommand command, PaymentMethod method) {
        return new PaymentExecutionResult(
                command.paymentKey(), command.orderId(), extraDetails(command.amount(), method),
                null, true, false, false, false);
    }

    public static PaymentExecutionResult failureResult(PaymentConfirmCommand command) {
        return new PaymentExecutionResult(
                command.paymentKey(), command.orderId(), null,
                new PaymentFailure("ERROR", "Test Error"), false, true, false, false);
    }
}
