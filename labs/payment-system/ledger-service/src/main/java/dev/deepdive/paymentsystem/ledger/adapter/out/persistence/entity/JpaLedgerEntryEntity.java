package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity;

import dev.deepdive.paymentsystem.ledger.domain.LedgerEntryType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "ledger_entries")
public class JpaLedgerEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal amount;

    @Column(name = "account_id")
    private Long accountId;

    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private JpaLedgerTransactionEntity transaction;

    @Enumerated(EnumType.STRING)
    private LedgerEntryType type;

    protected JpaLedgerEntryEntity() {
    }

    public JpaLedgerEntryEntity(
            BigDecimal amount,
            Long accountId,
            JpaLedgerTransactionEntity transaction,
            LedgerEntryType type
    ) {
        this.amount = amount;
        this.accountId = accountId;
        this.transaction = transaction;
        this.type = type;
    }

    public Long id() {
        return id;
    }

    public BigDecimal amount() {
        return amount;
    }

    public Long accountId() {
        return accountId;
    }

    public JpaLedgerTransactionEntity transaction() {
        return transaction;
    }

    public LedgerEntryType type() {
        return type;
    }
}
