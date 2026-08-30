package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity.JpaAccountEntity;
import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity.JpaAccountMapper;
import dev.deepdive.paymentsystem.ledger.domain.DoubleAccountsForLedger;
import dev.deepdive.paymentsystem.ledger.domain.FinanceType;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAccountRepository implements AccountRepository {

    static final String REVENUE_ACCOUNT_NAME = "REVENUE";
    static final String ITEM_BUYER_ACCOUNT_NAME = "ITEM_BUYER";

    private final SpringDataJpaAccountRepository springDataJpaAccountRepository;
    private final JpaAccountMapper jpaAccountMapper;

    public JpaAccountRepository(
            SpringDataJpaAccountRepository springDataJpaAccountRepository,
            JpaAccountMapper jpaAccountMapper
    ) {
        this.springDataJpaAccountRepository = springDataJpaAccountRepository;
        this.jpaAccountMapper = jpaAccountMapper;
    }

    @Override
    public DoubleAccountsForLedger getDoubleAccountsForLedger(FinanceType financeType) {
        return switch (financeType) {
            case PAYMENT_ORDER -> {
                JpaAccountEntity to = springDataJpaAccountRepository.findByName(REVENUE_ACCOUNT_NAME);
                JpaAccountEntity from = springDataJpaAccountRepository.findByName(ITEM_BUYER_ACCOUNT_NAME);

                yield new DoubleAccountsForLedger(
                        jpaAccountMapper.mapToDomainEntity(to),
                        jpaAccountMapper.mapToDomainEntity(from)
                );
            }
        };
    }
}
