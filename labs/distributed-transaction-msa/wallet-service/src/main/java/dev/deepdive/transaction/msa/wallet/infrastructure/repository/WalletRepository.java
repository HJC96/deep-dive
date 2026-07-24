package dev.deepdive.transaction.msa.wallet.infrastructure.repository;

import dev.deepdive.transaction.msa.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, Long> {}
