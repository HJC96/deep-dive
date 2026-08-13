package dev.deepdive.transaction.tcc.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTccLogRepository extends JpaRepository<WalletTccLog, Long> {
}
