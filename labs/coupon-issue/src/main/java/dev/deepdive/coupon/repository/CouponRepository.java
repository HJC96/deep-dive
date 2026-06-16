package dev.deepdive.coupon.repository;

import dev.deepdive.coupon.core.CouponStock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CouponRepository extends JpaRepository<CouponStock, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c
            FROM CouponStock c
            WHERE c.couponId = :couponId
            """)
    Optional<CouponStock> findByIdWithPessimisticLock(@Param("couponId") Long couponId);

    @Transactional
    @Modifying
    @Query("""
            UPDATE CouponStock c
            SET c.remainingQuantity = c.remainingQuantity - 1
            WHERE c.couponId = :couponId AND c.remainingQuantity >= 1
            """)
    int decreaseQuantity(@Param("couponId") Long couponId);
}
