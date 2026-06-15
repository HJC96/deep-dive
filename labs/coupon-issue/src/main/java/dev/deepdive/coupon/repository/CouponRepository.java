package dev.deepdive.coupon.repository;

import dev.deepdive.coupon.core.CouponStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CouponRepository extends JpaRepository<CouponStock, Long> {

    @Transactional
    @Modifying
    @Query("""
            UPDATE CouponStock c
            SET c.remainingQuantity = c.remainingQuantity - 1
            WHERE c.couponId = :couponId AND c.remainingQuantity >= 1
            """)
    int decreaseQuantity(@Param("couponId") Long couponId);
}
