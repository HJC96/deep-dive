package dev.deepdive.transaction.application.wallet;

import dev.deepdive.transaction.domain.wallet.Wallet;
import dev.deepdive.transaction.domain.wallet.WalletLedger;
import dev.deepdive.transaction.infrastructure.repository.wallet.WalletLedgerRepository;
import dev.deepdive.transaction.infrastructure.repository.wallet.WalletRepository;
import org.springframework.stereotype.Service;

@Service
public class WalletService {
    private final WalletRepository walletRepository;
    private final WalletLedgerRepository ledgerRepository;

    public WalletService(WalletRepository walletRepository, WalletLedgerRepository ledgerRepository) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
    }

    public void debit(long userId, long amount, String ledgerKey) {
        Wallet wallet = walletRepository.findById(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));
        wallet.debit(amount);
        ledgerRepository.save(new WalletLedger(ledgerKey, userId, amount));
    }
}
