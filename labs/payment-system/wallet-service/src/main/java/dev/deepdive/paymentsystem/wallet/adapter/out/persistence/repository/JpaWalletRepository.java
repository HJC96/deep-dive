package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletEntity;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletMapper;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletTransactionEntity;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.exception.RetryExhaustedWithOptimisticLockingFailureException;
import dev.deepdive.paymentsystem.wallet.application.exception.SettlementAlreadyProcessedException;
import dev.deepdive.paymentsystem.wallet.domain.Wallet;
import dev.deepdive.paymentsystem.wallet.domain.WalletTransaction;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Repository
public class JpaWalletRepository implements WalletRepository {

    /**
     * 첫 시도가 낙관적 락 충돌로 실패한 뒤 추가로 몇 번 더 시도하는지. 총 시도 횟수는 1 + MAX_RETRIES.
     * 다 써도 실패하면 예외를 던져 메시지 재시도(바인더 max-attempts) → DLQ 로 넘긴다.
     */
    static final int MAX_RETRIES = 3;
    static final long BASE_DELAY_MILLIS = 50;
    static final long MAX_DELAY_MILLIS = 1_000;

    private final SpringDataJpaWalletRepository springDataJpaWalletRepository;
    private final JpaWalletMapper jpaWalletMapper;
    private final WalletTransactionRepository walletTransactionRepository;
    private final TransactionTemplate transactionTemplate;
    private final OptimisticLockRetryBackoff backoff =
            new OptimisticLockRetryBackoff(BASE_DELAY_MILLIS, MAX_DELAY_MILLIS, () -> ThreadLocalRandom.current().nextDouble());

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
        // 지갑은 Set 에서 와서 순서가 매번 다르다. 동시에 정산하는 트랜잭션들이 행 락을 서로 다른 순서로 잡으면
        // 교착(deadlock)이 난다. 지갑은 id 순, 거래 INSERT 는 idempotency_key 순(JpaWalletTransactionRepository)으로 고정한다.
        List<Wallet> ordered = wallets.stream().sorted(Comparator.comparingLong(Wallet::id)).toList();
        try {
            saveWithOptimisticLockRetry(ordered);
        } catch (DataIntegrityViolationException e) {
            // 잔액 갱신과 거래 INSERT 는 한 트랜잭션이라 함께 롤백됐다. 같은 주문 라인이 이미 정산된 경우만 "이미 처리됨"으로 바꾼다.
            if (isDuplicateIdempotencyKey(e)) {
                throw new SettlementAlreadyProcessedException("이미 정산된 지갑 거래입니다.", e);
            }
            throw e;
        }
    }

    private void saveWithOptimisticLockRetry(List<Wallet> wallets) {
        try {
            performSaveOperation(wallets);
            return;
        } catch (ObjectOptimisticLockingFailureException | CannotAcquireLockException e) {
            // 아래에서 최신 잔액을 다시 읽어 재시도한다. 교착으로 희생된 트랜잭션도 전부 롤백됐으므로 같은 방식으로 다시 시도한다.
        }

        for (int retry = 1; ; retry++) {
            waitForNextRetry(retry);
            try {
                performSaveOperationWithRecent(wallets);
                return;
            } catch (ObjectOptimisticLockingFailureException | CannotAcquireLockException e) {
                if (retry >= MAX_RETRIES) {
                    throw new RetryExhaustedWithOptimisticLockingFailureException(
                            e.getMessage() != null ? e.getMessage() : "exhausted retry count.");
                }
            }
        }
    }

    private void performSaveOperation(List<Wallet> wallets) {
        transactionTemplate.executeWithoutResult(status -> {
            springDataJpaWalletRepository.saveAll(
                    wallets.stream().map(jpaWalletMapper::mapToJpaEntity).toList());
            walletTransactionRepository.save(flattenTransactions(wallets));
        });
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

    private void waitForNextRetry(int retry) {
        try {
            Thread.sleep(backoff.delayMillis(retry));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during retry wait", e);
        }
    }

    private static boolean isDuplicateIdempotencyKey(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(JpaWalletTransactionEntity.IDEMPOTENCY_KEY_CONSTRAINT)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
