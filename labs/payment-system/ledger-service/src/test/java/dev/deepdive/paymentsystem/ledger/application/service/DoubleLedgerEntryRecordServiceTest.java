package dev.deepdive.paymentsystem.ledger.application.service;

import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity.JpaLedgerEntryEntity;
import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository.SpringDataJpaLedgerEntryRepository;
import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository.SpringDataJpaLedgerTransactionRepository;
import dev.deepdive.paymentsystem.ledger.application.port.out.DuplicateMessageFilterPort;
import dev.deepdive.paymentsystem.ledger.application.port.out.LoadAccountPort;
import dev.deepdive.paymentsystem.ledger.application.port.out.LoadPaymentOrderPort;
import dev.deepdive.paymentsystem.ledger.application.port.out.SaveDoubleLedgerEntryPort;
import dev.deepdive.paymentsystem.ledger.domain.LedgerEventMessage;
import dev.deepdive.paymentsystem.ledger.domain.LedgerEventMessageType;
import dev.deepdive.paymentsystem.ledger.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.ledger.domain.PaymentEventMessageType;
import dev.deepdive.paymentsystem.ledger.domain.PaymentOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 원본처럼 실제 인프라(로컬 MySQL)가 떠 있어야 한다. CI(./gradlew test)에서는 태그로 제외된다.
@SpringBootTest
@Tag("ExternalIntegration")
class DoubleLedgerEntryRecordServiceTest {

    @Autowired private SpringDataJpaLedgerEntryRepository springDataJpaLedgerEntryRepository;
    @Autowired private SpringDataJpaLedgerTransactionRepository springDataJpaLedgerTransactionRepository;
    @Autowired private DuplicateMessageFilterPort duplicateMessageFilterPort;
    @Autowired private LoadAccountPort loadAccountPort;
    @Autowired private SaveDoubleLedgerEntryPort saveDoubleLedgerEntryPort;

    @BeforeEach
    void clean() {
        springDataJpaLedgerEntryRepository.deleteAll();
        springDataJpaLedgerTransactionRepository.deleteAll();
    }

    @Test
    void should_record_double_ledger_entries_successfully() {
        PaymentEventMessage paymentEventMessage = new PaymentEventMessage(
                PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS,
                Map.of("orderId", UUID.randomUUID().toString()),
                Map.of()
        );

        LoadPaymentOrderPort mockLoadPaymentOrderPort = mock(LoadPaymentOrderPort.class);
        when(mockLoadPaymentOrderPort.getPaymentOrders(paymentEventMessage.orderId())).thenReturn(List.of(
                new PaymentOrder(1L, 200L, paymentEventMessage.orderId()),
                new PaymentOrder(2L, 300L, paymentEventMessage.orderId())
        ));

        DoubleLedgerEntryRecordService doubleLedgerEntryRecordService = new DoubleLedgerEntryRecordService(
                duplicateMessageFilterPort,
                loadAccountPort,
                mockLoadPaymentOrderPort,
                saveDoubleLedgerEntryPort
        );

        LedgerEventMessage ledgerEventMessage =
                doubleLedgerEntryRecordService.recordDoubleLedgerEntry(paymentEventMessage);

        List<JpaLedgerEntryEntity> jpaLedgerEntryEntities = springDataJpaLedgerEntryRepository.findAll();

        BigDecimal sumOf = jpaLedgerEntryEntities.stream()
                .map(entry -> switch (entry.type()) {
                    case CREDIT -> entry.amount();
                    case DEBIT -> entry.amount().negate();
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(ledgerEventMessage.type()).isEqualTo(LedgerEventMessageType.SUCCESS);
        assertThat(ledgerEventMessage.payload().get("orderId")).isEqualTo(paymentEventMessage.orderId());
        assertThat(sumOf.intValue()).isEqualTo(0);
        assertThat(jpaLedgerEntryEntities.size()).isEqualTo(4);
    }
}
