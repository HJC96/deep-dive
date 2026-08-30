package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.wallet.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.wallet.domain.WalletTransaction;

import java.util.List;

public interface WalletTransactionRepository {

    boolean isExist(PaymentEventMessage paymentEventMessage);

    void save(List<WalletTransaction> walletTransactions);
}
