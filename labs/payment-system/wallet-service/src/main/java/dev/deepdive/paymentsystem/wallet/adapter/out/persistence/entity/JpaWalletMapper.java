package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity;

import dev.deepdive.paymentsystem.wallet.domain.Wallet;
import org.springframework.stereotype.Component;

@Component
public class JpaWalletMapper {

    public Wallet mapToDomainEntity(JpaWalletEntity jpaWalletEntity) {
        return new Wallet(
                jpaWalletEntity.id(),
                jpaWalletEntity.userId(),
                jpaWalletEntity.version(),
                jpaWalletEntity.balance()
        );
    }

    public JpaWalletEntity mapToJpaEntity(Wallet wallet) {
        return new JpaWalletEntity(
                wallet.id(),
                wallet.userId(),
                wallet.balance(),
                wallet.version()
        );
    }
}
