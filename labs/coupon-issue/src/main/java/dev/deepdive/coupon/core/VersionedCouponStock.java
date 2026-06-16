package dev.deepdive.coupon.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Objects;

// 낙관적 락 전용 엔티티. @Version은 이 엔티티에만 둬서, 버전 컬럼이 다른 발급 방식
// (락 없음/synchronized/비관적 락/원자적 UPDATE)에 영향을 주지 않도록 분리한다.
@Entity
@Table(name = "versioned_coupon_stock")
public class VersionedCouponStock {

    @Id
    @Column(name = "id")
    private Long couponId;

    @Version
    private Long version;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "quantity", nullable = false)
    private int remainingQuantity;

    protected VersionedCouponStock() {
    }

    public VersionedCouponStock(Long couponId, int totalQuantity) {
        if (totalQuantity < 0) {
            throw new IllegalArgumentException("전체 수량은 음수일 수 없습니다.");
        }
        this.couponId = Objects.requireNonNull(couponId, "쿠폰 ID는 필수입니다.");
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
    }

    public Long couponId() {
        return couponId;
    }

    public int totalQuantity() {
        return totalQuantity;
    }

    public int decrease() {
        int currentQuantity = remainingQuantity;
        Thread.yield();
        remainingQuantity = currentQuantity - 1;
        return remainingQuantity;
    }

    public int remainingQuantity() {
        return remainingQuantity;
    }
}
