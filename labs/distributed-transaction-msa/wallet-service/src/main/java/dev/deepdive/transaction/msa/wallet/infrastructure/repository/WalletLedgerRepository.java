package dev.deepdive.transaction.msa.wallet.infrastructure.repository;

import dev.deepdive.transaction.msa.wallet.domain.WalletLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletLedgerRepository extends JpaRepository<WalletLedger, Long> {}
