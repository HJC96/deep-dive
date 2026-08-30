package dev.deepdive.paymentsystem.payment.application.service;

import dev.deepdive.paymentsystem.payment.adapter.out.persistent.repository.PaymentOutboxRepository;
import dev.deepdive.paymentsystem.payment.application.port.out.DispatchEventMessagePort;
import dev.deepdive.paymentsystem.payment.application.port.out.LoadPendingPaymentEventMessagePort;
import dev.deepdive.paymentsystem.payment.application.port.out.PaymentStatusUpdateCommand;
import dev.deepdive.paymentsystem.payment.domain.PSPConfirmationStatus;
import dev.deepdive.paymentsystem.payment.domain.PaymentExecutionResult;
import dev.deepdive.paymentsystem.payment.domain.PaymentExtraDetails;
import dev.deepdive.paymentsystem.payment.domain.PaymentMethod;
import dev.deepdive.paymentsystem.payment.domain.PaymentType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Hooks;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 외부 메시지 큐와 연동되는 테스트. 로컬 Kafka와 MySQL이 떠 있어야 한다.
 */
@SpringBootTest
@Tag("ExternalIntegration")
class PaymentEventMessageRelayServiceTest {

    @Autowired
    private PaymentOutboxRepository paymentOutboxRepository;

    @Autowired
    private LoadPendingPaymentEventMessagePort loadPendingPaymentEventMessagePort;

    @Autowired
    private DispatchEventMessagePort dispatchEventMessagePort;

    @Test
    void should_dispatch_to_external_message_system() throws InterruptedException {
        Hooks.onOperatorDebug();

        PaymentEventMessageRelayService paymentEventMessageRelayService =
                new PaymentEventMessageRelayService(loadPendingPaymentEventMessagePort, dispatchEventMessagePort);

        PaymentStatusUpdateCommand command = new PaymentStatusUpdateCommand(new PaymentExecutionResult(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                new PaymentExtraDetails(
                        PaymentType.NORMAL,
                        PaymentMethod.EASY_PAY,
                        LocalDateTime.now(),
                        "test_order_name",
                        PSPConfirmationStatus.DONE,
                        50000L,
                        "{}"
                ),
                null,
                true,
                false,
                false,
                false
        ));

        paymentOutboxRepository.insertOutbox(command).block();

        paymentEventMessageRelayService.relay();

        Thread.sleep(10000);
    }
}
