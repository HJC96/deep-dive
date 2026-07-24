package dev.deepdive.transaction.msa.wallet.application;

import dev.deepdive.transaction.msa.wallet.domain.Wallet;
import dev.deepdive.transaction.msa.wallet.domain.WalletLedger;
import dev.deepdive.transaction.msa.wallet.infrastructure.repository.WalletLedgerRepository;
import dev.deepdive.transaction.msa.wallet.infrastructure.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
    private final WalletRepository walletRepository;
    private final WalletLedgerRepository ledgerRepository;

    public WalletService(WalletRepository walletRepository, WalletLedgerRepository ledgerRepository) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public void debit(long requestId, long userId, long amount) {
        Wallet wallet = walletRepository.findById(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));
        wallet.debit(amount);
        ledgerRepository.save(new WalletLedger(requestId, userId, amount));
    }
}
