package dev.deepdive.transaction.msa.wallet.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class WalletLedger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private long requestId;
    private long userId;
    private long amount;

    protected WalletLedger() {}

    public WalletLedger(long requestId, long userId, long amount) {
        this.requestId = requestId;
        this.userId = userId;
        this.amount = amount;
    }
}
