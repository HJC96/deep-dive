package dev.deepdive.paymentsystem.wallet.adapter.out.persistence;

import dev.deepdive.paymentsystem.wallet.common.PersistenceAdapter;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository.WalletRepository;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository.WalletTransactionRepository;
import dev.deepdive.paymentsystem.wallet.application.port.out.DuplicateMessageFilterPort;
import dev.deepdive.paymentsystem.wallet.application.port.out.LoadWalletPort;
import dev.deepdive.paymentsystem.wallet.application.port.out.SaveWalletPort;
import dev.deepdive.paymentsystem.wallet.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.wallet.domain.Wallet;

import java.util.List;
import java.util.Set;

@PersistenceAdapter
public class WalletPersistenceAdapter implements DuplicateMessageFilterPort, LoadWalletPort, SaveWalletPort {

    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletRepository walletRepository;

    public WalletPersistenceAdapter(
            WalletTransactionRepository walletTransactionRepository,
            WalletRepository walletRepository
    ) {
        this.walletTransactionRepository = walletTransactionRepository;
        this.walletRepository = walletRepository;
    }

    @Override
    public boolean isAlreadyProcess(PaymentEventMessage paymentEventMessage) {
        return walletTransactionRepository.isExist(paymentEventMessage);
    }

    @Override
    public Set<Wallet> getWallets(Set<Long> sellerIds) {
        return walletRepository.getWallets(sellerIds);
    }

    @Override
    public void save(List<Wallet> wallets) {
        walletRepository.save(wallets);
    }
}
