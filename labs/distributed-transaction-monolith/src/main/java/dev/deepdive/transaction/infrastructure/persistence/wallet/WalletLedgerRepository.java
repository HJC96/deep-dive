package dev.deepdive.transaction.infrastructure.persistence.wallet;

import dev.deepdive.transaction.domain.wallet.WalletLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletLedgerRepository extends JpaRepository<WalletLedger, Long> {}
