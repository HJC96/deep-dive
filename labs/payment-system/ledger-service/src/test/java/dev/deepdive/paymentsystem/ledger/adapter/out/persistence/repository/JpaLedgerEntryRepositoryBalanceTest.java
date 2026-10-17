package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity.JpaLedgerEntryEntity;
import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity.JpaLedgerTransactionEntity;
import dev.deepdive.paymentsystem.ledger.domain.LedgerEntryType;
import dev.deepdive.paymentsystem.ledger.domain.UnbalancedLedgerException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 저장 직전, 매핑된 JPA 행 기준으로 거래마다 차변 합 == 대변 합인지 확인하는 검사.
 */
class JpaLedgerEntryRepositoryBalanceTest {

    @Test
    void passes_when_every_transaction_is_balanced() {
        JpaLedgerTransactionEntity tx1 = transaction(1);
        JpaLedgerTransactionEntity tx2 = transaction(2);

        assertThatCode(() -> JpaLedgerEntryRepository.verifyBalancedPerTransaction(List.of(
                entry(100, tx1, LedgerEntryType.CREDIT), entry(100, tx1, LedgerEntryType.DEBIT),
                entry(250, tx2, LedgerEntryType.CREDIT), entry(250, tx2, LedgerEntryType.DEBIT))))
                .doesNotThrowAnyException();
    }

    @Test
    void fails_when_a_mapping_bug_flips_a_direction() {
        JpaLedgerTransactionEntity tx = transaction(1);

        assertThatThrownBy(() -> JpaLedgerEntryRepository.verifyBalancedPerTransaction(List.of(
                entry(100, tx, LedgerEntryType.CREDIT), entry(100, tx, LedgerEntryType.CREDIT))))
                .isInstanceOf(UnbalancedLedgerException.class);
    }

    @Test
    void fails_when_one_transaction_is_unbalanced_even_if_the_batch_total_is() {
        JpaLedgerTransactionEntity tx1 = transaction(1);
        JpaLedgerTransactionEntity tx2 = transaction(2);

        // 배치 전체로는 차변 200 == 대변 200 이지만 거래별로는 어긋난다.
        assertThatThrownBy(() -> JpaLedgerEntryRepository.verifyBalancedPerTransaction(List.of(
                entry(150, tx1, LedgerEntryType.CREDIT), entry(50, tx1, LedgerEntryType.DEBIT),
                entry(50, tx2, LedgerEntryType.CREDIT), entry(150, tx2, LedgerEntryType.DEBIT))))
                .isInstanceOf(UnbalancedLedgerException.class);
    }

    private static JpaLedgerTransactionEntity transaction(long referenceId) {
        return new JpaLedgerTransactionEntity("test", referenceId, "PAYMENT_ORDER", "order", "key-" + referenceId);
    }

    private static JpaLedgerEntryEntity entry(long amount, JpaLedgerTransactionEntity transaction, LedgerEntryType type) {
        return new JpaLedgerEntryEntity(BigDecimal.valueOf(amount), 1L, transaction, type);
    }
}
