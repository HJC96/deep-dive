package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletTransactionMapper;
import dev.deepdive.paymentsystem.wallet.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.wallet.domain.WalletTransaction;
import org.springframework.stereotype.Repository;

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
        springDataJpaWalletTransactionRepository.saveAll(
                walletTransactions.stream()
                        .map(jpaWalletTransactionMapper::mapToJpaEntity)
                        .toList()
        );
    }
}
