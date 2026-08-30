package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletEntity;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletMapper;
import dev.deepdive.paymentsystem.wallet.domain.Item;
import dev.deepdive.paymentsystem.wallet.domain.ReferenceType;
import dev.deepdive.paymentsystem.wallet.domain.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 지갑에 정산이 동시에 몰릴 때, 낙관적 락 충돌을 재시도로 흡수해 Lost Update 없이 모두 반영되는지 검증한다.
 * 실제 MySQL이 떠 있어야 한다.
 */
@SpringBootTest
@Tag("ExternalIntegration")
class JpaWalletRepositoryTest {

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

        List<Item> items1 = List.of(new Item(1000L, UUID.randomUUID().toString(), 1L, ReferenceType.PAYMENT_ORDER));
        List<Item> items2 = List.of(new Item(2000L, UUID.randomUUID().toString(), 2L, ReferenceType.PAYMENT_ORDER));
        List<Item> items3 = List.of(new Item(3000L, UUID.randomUUID().toString(), 3L, ReferenceType.PAYMENT_ORDER));

        // 세 개 모두 같은 시작 버전의 baseWallet1/2 에서 파생 → 동시 저장 시 2건은 낙관적 락 충돌
        Wallet updatedWallet1 = baseWallet1.calculateBalanceWith(items1);
        Wallet updatedWallet2 = baseWallet1.calculateBalanceWith(items2);
        Wallet updatedWallet3 = baseWallet1.calculateBalanceWith(items3);
        Wallet updatedWallet4 = baseWallet2.calculateBalanceWith(items1);
        Wallet updatedWallet5 = baseWallet2.calculateBalanceWith(items2);
        Wallet updatedWallet6 = baseWallet2.calculateBalanceWith(items3);

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
    }
}
