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
                IdempotencyCreator.create(idempotencySource(walletTransaction))
        );
    }

    /**
     * 같은 주문 라인(orderId + 참조 종류 + 참조 id)의 같은 방향(CREDIT/DEBIT) 거래는 한 번만 기록돼야 한다.
     * 레코드 전체(toString)가 아니라 이 의미 있는 조합으로 키를 만든다. 금액·지갑 id 가 달라도 같은 주문 라인이면 같은 키다.
     */
    static String idempotencySource(WalletTransaction walletTransaction) {
        return String.join(":",
                walletTransaction.orderId(),
                walletTransaction.referenceType().name(),
                String.valueOf(walletTransaction.referenceId()),
                walletTransaction.type().name());
    }
}
