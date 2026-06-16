package dev.deepdive.coupon.repository;

import dev.deepdive.coupon.core.VersionedCouponStock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VersionedCouponRepository extends JpaRepository<VersionedCouponStock, Long> {

    @Lock(LockModeType.OPTIMISTIC)
    @Query("""
            SELECT c
            FROM VersionedCouponStock c
            WHERE c.couponId = :couponId
            """)
    Optional<VersionedCouponStock> findByIdWithOptimisticLock(@Param("couponId") Long couponId);
}
