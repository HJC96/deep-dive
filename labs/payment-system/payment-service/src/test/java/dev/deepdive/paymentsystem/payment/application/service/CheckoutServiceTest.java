package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutCommand;
import dev.deepdive.paymentsystem.payment.application.port.in.CheckoutUseCase;
import dev.deepdive.paymentsystem.payment.domain.PaymentEvent;
import dev.deepdive.paymentsystem.payment.test.PaymentDatabaseHelper;
import dev.deepdive.paymentsystem.payment.test.PaymentTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PaymentTestConfiguration.class)
class CheckoutServiceTest {

    @Autowired
    private CheckoutUseCase checkoutUseCase;

    @Autowired
    private PaymentDatabaseHelper paymentDatabaseHelper;

    @BeforeEach
    void setup() {
        paymentDatabaseHelper.clean().block();
    }

    @Test
    void should_save_PaymentEvent_and_PaymentOrder_successfully() {
        String orderId = UUID.randomUUID().toString();
        CheckoutCommand command = new CheckoutCommand(1L, 1L, List.of(1L, 2L, 3L), orderId);

        StepVerifier.create(checkoutUseCase.checkout(command))
                .expectNextMatches(result -> result.amount() == 60000L && result.orderId().equals(orderId))
                .verifyComplete();

        PaymentEvent paymentEvent = paymentDatabaseHelper.getPayments(orderId);

        assertThat(paymentEvent.orderId()).isEqualTo(orderId);
        assertThat(paymentEvent.totalAmount()).isEqualTo(60000L);
        assertThat(paymentEvent.paymentOrders()).hasSize(command.productIds().size());
    }

    @Test
    void should_fail_when_saving_second_time_with_the_same_orderId() {
        String orderId = UUID.randomUUID().toString();
        CheckoutCommand command = new CheckoutCommand(1L, 1L, List.of(1L, 2L, 3L), orderId);

        checkoutUseCase.checkout(command).block();

        assertThatThrownBy(() -> checkoutUseCase.checkout(command).block())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
