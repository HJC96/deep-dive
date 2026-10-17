package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletEntity;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletMapper;
import dev.deepdive.paymentsystem.wallet.application.exception.SettlementAlreadyProcessedException;
import dev.deepdive.paymentsystem.wallet.domain.Item;
import dev.deepdive.paymentsystem.wallet.domain.ReferenceType;
import dev.deepdive.paymentsystem.wallet.domain.Wallet;
import dev.deepdive.paymentsystem.wallet.test.WalletIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 같은 지갑에 정산이 동시에 몰릴 때, 낙관적 락 충돌을 재시도로 흡수해 Lost Update 없이 모두 반영되는지 검증한다.
 */
class JpaWalletRepositoryTest extends WalletIntegrationTestSupport {

    @Autowired private JpaWalletRepository walletRepository;
    @Autowired private SpringDataJpaWalletRepository springDataJpaWalletRepository;
    @Autowired private SpringDataJpaWalletTransactionRepository springDataJpaWalletTransactionRepository;
    @Autowired private JpaWalletMapper jpaWalletMapper;

    @BeforeEach
    void clean() {
        springDataJpaWalletTransactionRepository.deleteAll();
        springDataJpaWalletRepository.deleteAll();
    }

    @RepeatedTest(5)
    void should_update_balance_successfully_when_saved_concurrently() throws Exception {
        JpaWalletEntity jpaWalletEntity1 = new JpaWalletEntity(null, 1L, BigDecimal.ZERO, 0);
        JpaWalletEntity jpaWalletEntity2 = new JpaWalletEntity(null, 2L, BigDecimal.ZERO, 0);
        springDataJpaWalletRepository.saveAll(List.of(jpaWalletEntity1, jpaWalletEntity2));

        Wallet baseWallet1 = jpaWalletMapper.mapToDomainEntity(jpaWalletEntity1);
        Wallet baseWallet2 = jpaWalletMapper.mapToDomainEntity(jpaWalletEntity2);

        // 주문 라인(참조 id)마다 한 지갑에만 정산된다. 같은 주문 라인을 두 지갑에 정산하면 idempotency_key 가 겹친다.
        String orderId1 = UUID.randomUUID().toString();
        String orderId2 = UUID.randomUUID().toString();
        String orderId3 = UUID.randomUUID().toString();

        // 세 개 모두 같은 시작 버전의 baseWallet1/2 에서 파생 → 동시 저장 시 2건은 낙관적 락 충돌
        Wallet updatedWallet1 = baseWallet1.calculateBalanceWith(List.of(item(1000L, orderId1, 1L)));
        Wallet updatedWallet2 = baseWallet1.calculateBalanceWith(List.of(item(2000L, orderId2, 2L)));
        Wallet updatedWallet3 = baseWallet1.calculateBalanceWith(List.of(item(3000L, orderId3, 3L)));
        Wallet updatedWallet4 = baseWallet2.calculateBalanceWith(List.of(item(1000L, orderId1, 4L)));
        Wallet updatedWallet5 = baseWallet2.calculateBalanceWith(List.of(item(2000L, orderId2, 5L)));
        Wallet updatedWallet6 = baseWallet2.calculateBalanceWith(List.of(item(3000L, orderId3, 6L)));

        ExecutorService executorService = Executors.newFixedThreadPool(3);
        try {
            Future<?> future1 = executorService.submit(() -> walletRepository.save(List.of(updatedWallet1, updatedWallet4)));
            Future<?> future2 = executorService.submit(() -> walletRepository.save(List.of(updatedWallet2, updatedWallet5)));
            Future<?> future3 = executorService.submit(() -> walletRepository.save(List.of(updatedWallet3, updatedWallet6)));
            future1.get();
            future2.get();
            future3.get();
        } finally {
            executorService.shutdown();
        }

        JpaWalletEntity retrievedWallet1 = springDataJpaWalletRepository.findById(baseWallet1.id()).orElseThrow();
        JpaWalletEntity retrievedWallet2 = springDataJpaWalletRepository.findById(baseWallet2.id()).orElseThrow();

        assertThat(retrievedWallet1.version()).isEqualTo(3);
        assertThat(retrievedWallet2.version()).isEqualTo(3);
        assertThat(retrievedWallet1.balance().intValue()).isEqualTo(6000);
        assertThat(retrievedWallet2.balance().intValue()).isEqualTo(6000);
        assertThat(springDataJpaWalletTransactionRepository.count()).isEqualTo(6);
    }

    @Test
    void should_roll_back_the_balance_and_report_already_processed_on_a_duplicate_transaction() {
        JpaWalletEntity entity = springDataJpaWalletRepository.save(new JpaWalletEntity(null, 1L, BigDecimal.ZERO, 0));
        String orderId = UUID.randomUUID().toString();

        Wallet first = jpaWalletMapper.mapToDomainEntity(entity).calculateBalanceWith(List.of(item(1000L, orderId, 1L)));
        walletRepository.save(List.of(first));

        Wallet reloaded = jpaWalletMapper.mapToDomainEntity(springDataJpaWalletRepository.findById(entity.id()).orElseThrow());
        Wallet duplicate = reloaded.calculateBalanceWith(List.of(item(1000L, orderId, 1L)));

        assertThatThrownBy(() -> walletRepository.save(List.of(duplicate)))
                .isInstanceOf(SettlementAlreadyProcessedException.class);

        JpaWalletEntity after = springDataJpaWalletRepository.findById(entity.id()).orElseThrow();
        assertThat(after.balance().intValue()).isEqualTo(1000);
        assertThat(springDataJpaWalletTransactionRepository.count()).isEqualTo(1);
    }

    private static Item item(long amount, String orderId, long referenceId) {
        return new Item(amount, orderId, referenceId, ReferenceType.PAYMENT_ORDER);
    }
}
