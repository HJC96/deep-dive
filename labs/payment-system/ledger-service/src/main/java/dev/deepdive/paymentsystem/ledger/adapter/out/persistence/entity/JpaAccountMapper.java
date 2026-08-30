package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity;

import dev.deepdive.paymentsystem.ledger.domain.Account;
import org.springframework.stereotype.Component;

@Component
public class JpaAccountMapper {

    public Account mapToDomainEntity(JpaAccountEntity jpaAccountEntity) {
        return new Account(
                jpaAccountEntity.id(),
                jpaAccountEntity.name()
        );
    }
}
