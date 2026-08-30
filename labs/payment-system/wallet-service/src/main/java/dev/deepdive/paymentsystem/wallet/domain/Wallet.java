package dev.deepdive.paymentsystem.wallet.domain;

import java.math.BigDecimal;
import java.util.List;

public class Wallet {

    private final long id;
    private final long userId;
    private final int version;
    private final BigDecimal balance;
    private final List<WalletTransaction> walletTransactions;

    public Wallet(long id, long userId, int version, BigDecimal balance) {
        this(id, userId, version, balance, List.of());
    }

    public Wallet(long id, long userId, int version, BigDecimal balance, List<WalletTransaction> walletTransactions) {
        this.id = id;
        this.userId = userId;
        this.version = version;
        this.balance = balance;
        this.walletTransactions = walletTransactions == null ? List.of() : walletTransactions;
    }

    public Wallet calculateBalanceWith(List<? extends Item> items) {
        long addedAmount = items.stream().mapToLong(Item::amount).sum();

        List<WalletTransaction> transactions = items.stream()
                .map(item -> new WalletTransaction(
                        this.id,
                        item.amount(),
                        TransactionType.CREDIT,
                        item.referenceId(),
                        item.referenceType(),
                        item.orderId()
                ))
                .toList();

        return new Wallet(id, userId, version, balance.add(BigDecimal.valueOf(addedAmount)), transactions);
    }

    public long id() {
        return id;
    }

    public long userId() {
        return userId;
    }

    public int version() {
        return version;
    }

    public BigDecimal balance() {
        return balance;
    }

    public List<WalletTransaction> walletTransactions() {
        return walletTransactions;
    }
}
