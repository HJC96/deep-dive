package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity.JpaWalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface SpringDataJpaWalletRepository extends JpaRepository<JpaWalletEntity, Long> {

    List<JpaWalletEntity> findByUserIdIn(Set<Long> userIds);

    List<JpaWalletEntity> findByIdIn(Set<Long> ids);
}
