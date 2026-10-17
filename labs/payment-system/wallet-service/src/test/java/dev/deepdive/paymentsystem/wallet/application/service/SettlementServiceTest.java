package dev.deepdive.paymentsystem.wallet.application.service;

import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletEntity;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository.SpringDataJpaWalletRepository;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository.SpringDataJpaWalletTransactionRepository;
import dev.deepdive.paymentsystem.wallet.application.exception.WalletNotFoundException;
import dev.deepdive.paymentsystem.wallet.application.port.out.DuplicateMessageFilterPort;
import dev.deepdive.paymentsystem.wallet.application.port.out.LoadWalletPort;
import dev.deepdive.paymentsystem.wallet.application.port.out.SaveWalletPort;
import dev.deepdive.paymentsystem.wallet.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.wallet.domain.PaymentEventMessageType;
import dev.deepdive.paymentsystem.wallet.domain.Wallet;
import dev.deepdive.paymentsystem.wallet.domain.WalletEventMessage;
import dev.deepdive.paymentsystem.wallet.domain.WalletEventMessageType;
import dev.deepdive.paymentsystem.wallet.test.PaymentEventMessages;
import dev.deepdive.paymentsystem.wallet.test.PaymentEventMessages.Line;
import dev.deepdive.paymentsystem.wallet.test.WalletIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementServiceTest extends WalletIntegrationTestSupport {

    @Autowired private SettlementService settlementService;
    @Autowired private LoadWalletPort loadWalletPort;
    @Autowired private SaveWalletPort saveWalletPort;
    @Autowired private SpringDataJpaWalletRepository springDataJpaWalletRepository;
    @Autowired private SpringDataJpaWalletTransactionRepository springDataJpaWalletTransactionRepository;

    @BeforeEach
    void clean() {
        springDataJpaWalletTransactionRepository.deleteAll();
        springDataJpaWalletRepository.deleteAll();
        springDataJpaWalletRepository.saveAll(List.of(
                new JpaWalletEntity(null, 1L, BigDecimal.ZERO, 0),
                new JpaWalletEntity(null, 2L, BigDecimal.ZERO, 0)
        ));
    }

    @Test
    void should_process_settlement_with_the_order_lines_carried_in_the_message() {
        String orderId = UUID.randomUUID().toString();
        PaymentEventMessage message = PaymentEventMessages.paymentConfirmed(orderId,
                new Line(11, 1, 3000), new Line(12, 2, 4000), new Line(13, 1, 500));

        WalletEventMessage walletEventMessage = settlementService.processSettlement(message);

        assertThat(walletEventMessage.payload().get("orderId")).isEqualTo(orderId);
        assertThat(walletEventMessage.type()).isEqualTo(WalletEventMessageType.SUCCESS);
        assertThat(balances()).containsExactly(3500, 4000);
        assertThat(springDataJpaWalletTransactionRepository.count()).isEqualTo(3);
    }

    @Test
    void should_be_processed_only_once_even_if_called_multiple_times() {
        PaymentEventMessage message = PaymentEventMessages.paymentConfirmed(UUID.randomUUID().toString(),
                new Line(21, 1, 3000), new Line(22, 2, 4000));

        settlementService.processSettlement(message);
        settlementService.processSettlement(message);

        assertThat(balances()).containsExactly(3000, 4000);
    }

    @Test
    void should_rely_on_the_unique_constraint_when_the_duplicate_check_is_bypassed() {
        // 조회 후 저장(check-then-act)의 틈: 두 요청이 모두 "아직 없음"을 본 상황을 결정적으로 만든다.
        DuplicateMessageFilterPort alwaysNew = message -> false;
        SettlementService service = new SettlementService(alwaysNew, loadWalletPort, saveWalletPort);
        PaymentEventMessage message = PaymentEventMessages.paymentConfirmed(UUID.randomUUID().toString(),
                new Line(31, 1, 3000), new Line(32, 2, 4000));

        WalletEventMessage first = service.processSettlement(message);
        WalletEventMessage second = service.processSettlement(message);

        assertThat(first.type()).isEqualTo(WalletEventMessageType.SUCCESS);
        assertThat(second.type()).isEqualTo(WalletEventMessageType.SUCCESS);
        assertThat(balances()).containsExactly(3000, 4000);
        assertThat(springDataJpaWalletTransactionRepository.count()).isEqualTo(2);
    }

    /**
     * 같은 메시지가 동시에 여러 번 배달돼도 한 번만 정산돼야 한다. 늦은 쪽은 UNIQUE 위반을 "이미 처리됨" 성공으로 받는다.
     */
    @RepeatedTest(3)
    void should_settle_only_once_when_the_same_message_is_processed_concurrently() throws Exception {
        PaymentEventMessage message = PaymentEventMessages.paymentConfirmed(UUID.randomUUID().toString(),
                new Line(41, 1, 3000), new Line(42, 2, 4000));

        List<WalletEventMessage> results = runConcurrently(6, () -> settlementService.processSettlement(message));

        assertThat(results).hasSize(6).allMatch(it -> it.type() == WalletEventMessageType.SUCCESS);
        assertThat(balances()).containsExactly(3000, 4000);
        assertThat(springDataJpaWalletTransactionRepository.count()).isEqualTo(2);
    }

    @Test
    void should_fail_instead_of_silently_skipping_a_seller_without_wallet() {
        PaymentEventMessage message = PaymentEventMessages.paymentConfirmed(UUID.randomUUID().toString(),
                new Line(51, 1, 3000), new Line(52, 999, 4000));

        assertThatThrownBy(() -> settlementService.processSettlement(message))
                .isInstanceOfSatisfying(WalletNotFoundException.class,
                        e -> assertThat(e.sellerIds()).containsExactly(999L));

        // 일부(판매자 1)만 정산하고 끝내지 않는다.
        assertThat(balances()).containsExactly(0, 0);
        assertThat(springDataJpaWalletTransactionRepository.count()).isZero();
    }

    @Test
    void should_reject_a_legacy_message_without_order_lines() {
        PaymentEventMessage legacy = new PaymentEventMessage(
                PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS,
                Map.of("orderId", UUID.randomUUID().toString()),
                Map.of());

        assertThatThrownBy(() -> settlementService.processSettlement(legacy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paymentOrders");
    }

    private List<Integer> balances() {
        return loadWalletPort.getWallets(Set.of(1L, 2L)).stream()
                .sorted(Comparator.comparingLong(Wallet::userId))
                .map(it -> it.balance().intValue())
                .toList();
    }

    private static <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executorService.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executorService.shutdownNow();
        }
    }
}
