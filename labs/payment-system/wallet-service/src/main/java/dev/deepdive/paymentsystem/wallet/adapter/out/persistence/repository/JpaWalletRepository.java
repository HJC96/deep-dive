package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletEntity;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletMapper;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.exception.RetryExhaustedWithOptimisticLockingFailureException;
import dev.deepdive.paymentsystem.wallet.domain.Wallet;
import dev.deepdive.paymentsystem.wallet.domain.WalletTransaction;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class JpaWalletRepository implements WalletRepository {

    private static final int MAX_RETRIES = 3;
    private static final int BASE_DELAY = 100;

    private final SpringDataJpaWalletRepository springDataJpaWalletRepository;
    private final JpaWalletMapper jpaWalletMapper;
    private final WalletTransactionRepository walletTransactionRepository;
    private final TransactionTemplate transactionTemplate;

    public JpaWalletRepository(
            SpringDataJpaWalletRepository springDataJpaWalletRepository,
            JpaWalletMapper jpaWalletMapper,
            WalletTransactionRepository walletTransactionRepository,
            TransactionTemplate transactionTemplate
    ) {
        this.springDataJpaWalletRepository = springDataJpaWalletRepository;
        this.jpaWalletMapper = jpaWalletMapper;
        this.walletTransactionRepository = walletTransactionRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public Set<Wallet> getWallets(Set<Long> sellerIds) {
        return springDataJpaWalletRepository.findByUserIdIn(sellerIds).stream()
                .map(jpaWalletMapper::mapToDomainEntity)
                .collect(Collectors.toSet());
    }

    @Override
    public void save(List<Wallet> wallets) {
        try {
            performSaveOperation(wallets);
        } catch (ObjectOptimisticLockingFailureException e) {
            retrySaveOperation(wallets);
        }
    }

    private void performSaveOperation(List<Wallet> wallets) {
        transactionTemplate.executeWithoutResult(status -> {
            springDataJpaWalletRepository.saveAll(
                    wallets.stream().map(jpaWalletMapper::mapToJpaEntity).toList());
            walletTransactionRepository.save(flattenTransactions(wallets));
        });
    }

    private void retrySaveOperation(List<Wallet> wallets) {
        int retryCount = 0;

        while (true) {
            try {
                performSaveOperationWithRecent(wallets);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (++retryCount > MAX_RETRIES) {
                    throw new RetryExhaustedWithOptimisticLockingFailureException(
                            e.getMessage() != null ? e.getMessage() : "exhausted retry count.");
                }
                waitForNextRetry(BASE_DELAY);
            }
        }
    }

    private void performSaveOperationWithRecent(List<Wallet> wallets) {
        Set<Long> ids = wallets.stream().map(Wallet::id).collect(Collectors.toSet());

        Map<Long, JpaWalletEntity> recentWalletsById = springDataJpaWalletRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(JpaWalletEntity::id, entity -> entity));

        List<JpaWalletEntity> updatedWallets = wallets.stream()
                .map(wallet -> {
                    JpaWalletEntity recent = recentWalletsById.get(wallet.id());
                    long addedAmount = wallet.walletTransactions().stream()
                            .mapToLong(WalletTransaction::amount)
                            .sum();
                    return recent.addBalance(BigDecimal.valueOf(addedAmount));
                })
                .toList();

        transactionTemplate.executeWithoutResult(status -> {
            springDataJpaWalletRepository.saveAll(updatedWallets);
            walletTransactionRepository.save(flattenTransactions(wallets));
        });
    }

    private List<WalletTransaction> flattenTransactions(List<Wallet> wallets) {
        return wallets.stream()
                .flatMap(wallet -> wallet.walletTransactions().stream())
                .toList();
    }

    private void waitForNextRetry(int baseDelay) {
        long jitter = (long) (Math.random() * baseDelay);

        try {
            Thread.sleep(jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry wait", e);
        }
    }
}
