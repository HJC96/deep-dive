package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity.JpaLedgerEntryEntity;
import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity.JpaLedgerEntryMapper;
import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity.JpaLedgerTransactionEntity;
import dev.deepdive.paymentsystem.ledger.application.exception.LedgerAlreadyRecordedException;
import dev.deepdive.paymentsystem.ledger.domain.DoubleLedgerEntry;
import dev.deepdive.paymentsystem.ledger.domain.LedgerEntryType;
import dev.deepdive.paymentsystem.ledger.domain.UnbalancedLedgerException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Repository
public class JpaLedgerEntryRepository implements LedgerEntryRepository {

    private static final int MAX_DEADLOCK_ATTEMPTS = 3;

    private final SpringDataJpaLedgerEntryRepository springDataJpaLedgerEntryRepository;
    private final JpaLedgerEntryMapper jpaLedgerEntryMapper;
    private final TransactionTemplate transactionTemplate;

    public JpaLedgerEntryRepository(
            SpringDataJpaLedgerEntryRepository springDataJpaLedgerEntryRepository,
            JpaLedgerEntryMapper jpaLedgerEntryMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.springDataJpaLedgerEntryRepository = springDataJpaLedgerEntryRepository;
        this.jpaLedgerEntryMapper = jpaLedgerEntryMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void save(List<DoubleLedgerEntry> doubleLedgerEntries) {
        // 동시에 같은 메시지를 기록하는 트랜잭션들이 UNIQUE 인덱스에 같은 순서(인덱스 키 순)로 들어가도록
        // idempotency_key 순으로 정렬한다. 순서가 엇갈리면 중복 키 대기끼리 교착이 난다.
        List<JpaLedgerEntryEntity> entities = doubleLedgerEntries.stream()
                .flatMap(entry -> jpaLedgerEntryMapper.mapToJpaEntity(entry).stream())
                .sorted(Comparator.comparing((JpaLedgerEntryEntity entity) -> entity.transaction().idempotencyKey()))
                .toList();

        // 도메인 검사와 별개로, 실제로 저장할 행(매핑 결과) 기준으로 거래마다 차변 합 == 대변 합인지 한 번 더 본다.
        // 매퍼가 금액·방향을 잘못 옮기는 실수를 append-only 테이블에 들어가기 전에 잡는다.
        verifyBalancedPerTransaction(entities);

        try {
            saveWithDeadlockRetry(entities);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateIdempotencyKey(e)) {
                throw new LedgerAlreadyRecordedException("이미 기록된 장부 거래입니다.", e);
            }
            throw e;
        }
    }

    /**
     * InnoDB 는 같은 UNIQUE 키에 여러 트랜잭션이 동시에 INSERT 하면 정렬해도 교착을 낼 수 있다.
     * 희생된 트랜잭션은 통째로 롤백됐으므로 잠깐 쉬었다 다시 시도한다. 다시 시도하면 대개 UNIQUE 위반(이미 기록됨)으로 끝난다.
     */
    private void saveWithDeadlockRetry(List<JpaLedgerEntryEntity> entities) {
        for (int attempt = 1; ; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status -> springDataJpaLedgerEntryRepository.saveAll(entities));
                return;
            } catch (CannotAcquireLockException e) {
                if (attempt >= MAX_DEADLOCK_ATTEMPTS) {
                    throw e;
                }
                sleepBeforeRetry(attempt);
            }
        }
    }

    private static void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(10, 50L * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during retry wait", e);
        }
    }

    static void verifyBalancedPerTransaction(List<JpaLedgerEntryEntity> entities) {
        Map<JpaLedgerTransactionEntity, BigDecimal[]> sums = new IdentityHashMap<>();
        for (JpaLedgerEntryEntity entity : entities) {
            BigDecimal[] sum = sums.computeIfAbsent(entity.transaction(), it -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            int index = entity.type() == LedgerEntryType.DEBIT ? 0 : 1;
            sum[index] = sum[index].add(entity.amount());
        }
        sums.forEach((transaction, sum) -> {
            if (sum[0].compareTo(sum[1]) != 0) {
                throw new UnbalancedLedgerException(
                        "장부 거래 (orderId: " + transaction.orderId() + ", referenceId: " + transaction.referenceId()
                                + ") 의 차변 합(" + sum[0] + ")과 대변 합(" + sum[1] + ")이 다릅니다.");
            }
        });
    }

    private static boolean isDuplicateIdempotencyKey(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(JpaLedgerTransactionEntity.IDEMPOTENCY_KEY_CONSTRAINT)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
