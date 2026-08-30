package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity;

import dev.deepdive.paymentsystem.wallet.common.IdempotencyCreator;
import dev.deepdive.paymentsystem.wallet.domain.WalletTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class JpaWalletTransactionMapper {

    public JpaWalletTransactionEntity mapToJpaEntity(WalletTransaction walletTransaction) {
        return new JpaWalletTransactionEntity(
                walletTransaction.walletId(),
                BigDecimal.valueOf(walletTransaction.amount()),
                walletTransaction.type(),
                walletTransaction.orderId(),
                walletTransaction.referenceType().name(),
                walletTransaction.referenceId(),
                IdempotencyCreator.create(walletTransaction)
        );
    }
}
