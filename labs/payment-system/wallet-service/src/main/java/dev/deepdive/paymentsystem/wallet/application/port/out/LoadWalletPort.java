package dev.deepdive.paymentsystem.wallet.application.port.out;

import dev.deepdive.paymentsystem.wallet.domain.Wallet;

import java.util.Set;

public interface LoadWalletPort {

    Set<Wallet> getWallets(Set<Long> sellerIds);
}
