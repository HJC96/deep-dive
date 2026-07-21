package dev.deepdive.transaction.infrastructure.persistence.wallet;

import dev.deepdive.transaction.domain.wallet.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, Long> {}
