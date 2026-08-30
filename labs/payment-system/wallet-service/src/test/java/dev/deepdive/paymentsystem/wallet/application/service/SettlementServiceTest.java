package dev.deepdive.paymentsystem.wallet.application.service;

import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletEntity;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository.SpringDataJpaWalletRepository;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository.SpringDataJpaWalletTransactionRepository;
import dev.deepdive.paymentsystem.wallet.application.port.out.DuplicateMessageFilterPort;
import dev.deepdive.paymentsystem.wallet.application.port.out.LoadPaymentOrderPort;
import dev.deepdive.paymentsystem.wallet.application.port.out.LoadWalletPort;
import dev.deepdive.paymentsystem.wallet.application.port.out.SaveWalletPort;
import dev.deepdive.paymentsystem.wallet.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.wallet.domain.PaymentEventMessageType;
import dev.deepdive.paymentsystem.wallet.domain.PaymentOrder;
import dev.deepdive.paymentsystem.wallet.domain.Wallet;
import dev.deepdive.paymentsystem.wallet.domain.WalletEventMessage;
import dev.deepdive.paymentsystem.wallet.domain.WalletEventMessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 원본처럼 실제 인프라(로컬 MySQL + Kafka)가 떠 있어야 한다. Kafka 없이 돌리면 바인더 프로비저닝
// 재시도로 컨텍스트 기동이 매우 느리다(수 분+). CI(./gradlew test)에서는 태그로 제외된다.
@SpringBootTest
@Tag("ExternalIntegration")
class SettlementServiceTest {

    @Autowired private DuplicateMessageFilterPort duplicateMessageFilterPort;
    @Autowired private LoadWalletPort loadWalletPort;
    @Autowired private SaveWalletPort saveWalletPort;
    @Autowired private SpringDataJpaWalletRepository springDataJpaWalletRepository;
    @Autowired private SpringDataJpaWalletTransactionRepository springDataJpaWalletTransactionRepository;

    @BeforeEach
    void clean() {
        springDataJpaWalletTransactionRepository.deleteAll();
        springDataJpaWalletRepository.deleteAll();
    }

    @Test
    void should_process_settlement_successfully() {
        springDataJpaWalletRepository.saveAll(List.of(
                new JpaWalletEntity(null, 1L, BigDecimal.ZERO, 0),
                new JpaWalletEntity(null, 2L, BigDecimal.ZERO, 0)
        ));

        String orderId = UUID.randomUUID().toString();
        PaymentEventMessage paymentEventMessage = new PaymentEventMessage(
                PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS,
                Map.of("orderId", orderId),
                Map.of()
        );

        LoadPaymentOrderPort mockLoadPaymentOrderPort = mock(LoadPaymentOrderPort.class);
        when(mockLoadPaymentOrderPort.getPaymentOrders(eq(orderId))).thenReturn(List.of(
                new PaymentOrder(1, 1, 3000L, orderId),
                new PaymentOrder(2, 2, 4000L, orderId)
        ));

        SettlementService settlementService = new SettlementService(
                duplicateMessageFilterPort, mockLoadPaymentOrderPort, loadWalletPort, saveWalletPort);

        WalletEventMessage walletEventMessage = settlementService.processSettlement(paymentEventMessage);

        List<Wallet> updatedWallets = loadWalletPort.getWallets(Set.of(1L, 2L)).stream()
                .sorted(Comparator.comparingLong(Wallet::userId))
                .toList();

        assertThat(walletEventMessage.payload().get("orderId")).isEqualTo(orderId);
        assertThat(walletEventMessage.type()).isEqualTo(WalletEventMessageType.SUCCESS);
        assertThat(updatedWallets.get(0).balance().intValue()).isEqualTo(3000);
        assertThat(updatedWallets.get(1).balance().intValue()).isEqualTo(4000);
    }

    @Test
    void should_be_processed_only_once_even_if_called_multiple_times() {
        springDataJpaWalletRepository.saveAll(List.of(
                new JpaWalletEntity(null, 1L, BigDecimal.ZERO, 0),
                new JpaWalletEntity(null, 2L, BigDecimal.ZERO, 0)
        ));

        String orderId = UUID.randomUUID().toString();
        PaymentEventMessage paymentEventMessage = new PaymentEventMessage(
                PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS,
                Map.of("orderId", orderId),
                Map.of()
        );

        LoadPaymentOrderPort mockLoadPaymentOrderPort = mock(LoadPaymentOrderPort.class);
        when(mockLoadPaymentOrderPort.getPaymentOrders(eq(orderId))).thenReturn(List.of(
                new PaymentOrder(1, 1, 3000L, orderId),
                new PaymentOrder(2, 2, 4000L, orderId)
        ));

        SettlementService settlementService = new SettlementService(
                duplicateMessageFilterPort, mockLoadPaymentOrderPort, loadWalletPort, saveWalletPort);

        settlementService.processSettlement(paymentEventMessage);
        settlementService.processSettlement(paymentEventMessage);

        List<Wallet> updatedWallets = loadWalletPort.getWallets(Set.of(1L, 2L)).stream()
                .sorted(Comparator.comparingLong(Wallet::userId))
                .toList();

        assertThat(updatedWallets.get(0).balance().intValue()).isEqualTo(3000);
        assertThat(updatedWallets.get(1).balance().intValue()).isEqualTo(4000);
    }
}
