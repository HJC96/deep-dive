package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletTransactionEntity;
import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletTransactionMapper;
import dev.deepdive.paymentsystem.wallet.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.wallet.domain.WalletTransaction;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;

@Repository
public class JpaWalletTransactionRepository implements WalletTransactionRepository {

    private final SpringDataJpaWalletTransactionRepository springDataJpaWalletTransactionRepository;
    private final JpaWalletTransactionMapper jpaWalletTransactionMapper;

    public JpaWalletTransactionRepository(
            SpringDataJpaWalletTransactionRepository springDataJpaWalletTransactionRepository,
            JpaWalletTransactionMapper jpaWalletTransactionMapper
    ) {
        this.springDataJpaWalletTransactionRepository = springDataJpaWalletTransactionRepository;
        this.jpaWalletTransactionMapper = jpaWalletTransactionMapper;
    }

    @Override
    public boolean isExist(PaymentEventMessage paymentEventMessage) {
        return springDataJpaWalletTransactionRepository.existsByOrderId(paymentEventMessage.orderId());
    }

    @Override
    public void save(List<WalletTransaction> walletTransactions) {
        // 동시에 같은 메시지를 정산하는 트랜잭션들이 UNIQUE 인덱스에 같은 순서(인덱스 키 순)로 들어가도록 정렬한다(교착 완화).
        springDataJpaWalletTransactionRepository.saveAll(
                walletTransactions.stream()
                        .map(jpaWalletTransactionMapper::mapToJpaEntity)
                        .sorted(Comparator.comparing(JpaWalletTransactionEntity::idempotencyKey))
                        .toList()
        );
    }
}
