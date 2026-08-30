package dev.deepdive.paymentsystem.wallet.domain;

public record WalletTransaction(
        long walletId,
        long amount,
        TransactionType type,
        long referenceId,
        ReferenceType referenceType,
        String orderId
) {
}
