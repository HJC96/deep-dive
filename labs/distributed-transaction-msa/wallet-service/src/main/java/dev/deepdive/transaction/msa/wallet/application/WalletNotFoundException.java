package dev.deepdive.transaction.msa.wallet.application;

public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(long userId) {
        super("wallet not found: " + userId);
    }
}
