package dev.deepdive.transaction.domain.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class WalletLedger {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String ledgerKey;
    private long userId;
    private long amount;

    protected WalletLedger() {}

    public WalletLedger(String ledgerKey, long userId, long amount) {
        this.ledgerKey = ledgerKey;
        this.userId = userId;
        this.amount = amount;
    }
}
