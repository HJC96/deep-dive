package dev.deepdive.paymentsystem.wallet.application.port.out;

import dev.deepdive.paymentsystem.wallet.domain.Wallet;

import java.util.List;

public interface SaveWalletPort {

    void save(List<Wallet> wallets);
}
