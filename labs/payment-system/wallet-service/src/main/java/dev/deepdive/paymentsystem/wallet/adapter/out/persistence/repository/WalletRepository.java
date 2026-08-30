package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.wallet.domain.Wallet;

import java.util.List;
import java.util.Set;

public interface WalletRepository {

    Set<Wallet> getWallets(Set<Long> sellerIds);

    void save(List<Wallet> wallets);
}
