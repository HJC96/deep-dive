package dev.deepdive.transaction.domain.wallet;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Wallet {
    @Id
    private Long userId;
    private long balance;

    protected Wallet() {}

    public Wallet(long userId, long balance) {
        if (balance < 0) throw new IllegalArgumentException("balance cannot be negative");
        this.userId = userId;
        this.balance = balance;
    }

    public void debit(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        if (balance < amount) throw new NotEnoughCreditException();
        balance -= amount;
    }

    public long getBalance() { return balance; }
}
