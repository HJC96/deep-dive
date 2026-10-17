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
                JpaAccountEntity to = findAccount(REVENUE_ACCOUNT_NAME);
                JpaAccountEntity from = findAccount(ITEM_BUYER_ACCOUNT_NAME);

                yield new DoubleAccountsForLedger(
                        jpaAccountMapper.mapToDomainEntity(to),
                        jpaAccountMapper.mapToDomainEntity(from)
                );
            }
        };
    }

    private JpaAccountEntity findAccount(String name) {
        JpaAccountEntity account = springDataJpaAccountRepository.findByName(name);
        if (account == null) {
            throw new IllegalStateException("장부 계정 (name: " + name + ") 이 없습니다. schema.sql 의 accounts 시딩을 확인하세요.");
        }
        return account;
    }
}
