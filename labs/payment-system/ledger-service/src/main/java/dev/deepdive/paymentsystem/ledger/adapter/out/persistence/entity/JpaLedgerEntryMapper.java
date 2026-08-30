package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity;

import dev.deepdive.paymentsystem.ledger.domain.DoubleLedgerEntry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class JpaLedgerEntryMapper {

    private final JpaLedgerTransactionMapper jpaLedgerTransactionMapper;

    public JpaLedgerEntryMapper(JpaLedgerTransactionMapper jpaLedgerTransactionMapper) {
        this.jpaLedgerTransactionMapper = jpaLedgerTransactionMapper;
    }

    public List<JpaLedgerEntryEntity> mapToJpaEntity(DoubleLedgerEntry doubleLedgerEntry) {
        JpaLedgerTransactionEntity jpaLedgerTransactionEntity =
                jpaLedgerTransactionMapper.mapToJpaEntity(doubleLedgerEntry.transaction());
        return List.of(
                new JpaLedgerEntryEntity(
                        BigDecimal.valueOf(doubleLedgerEntry.credit().amount()),
                        doubleLedgerEntry.credit().account().id(),
                        jpaLedgerTransactionEntity,
                        doubleLedgerEntry.credit().type()
                ),
                new JpaLedgerEntryEntity(
                        BigDecimal.valueOf(doubleLedgerEntry.debit().amount()),
                        doubleLedgerEntry.debit().account().id(),
                        jpaLedgerTransactionEntity,
                        doubleLedgerEntry.debit().type()
                )
        );
    }
}
