package dev.deepdive.coupon.repository;

import dev.deepdive.coupon.core.CouponStock;
import java.util.Objects;

public final class InMemoryCouponRepository implements CouponRepository {

    private CouponStock couponStock;

    @Override
    public void save(CouponStock couponStock) {
        this.couponStock = Objects.requireNonNull(couponStock, "쿠폰 재고는 필수입니다.");
    }

    @Override
    public CouponStock findById(Long couponId) {
        if (couponStock == null || !couponStock.couponId().equals(couponId)) {
            throw new IllegalArgumentException("쿠폰 재고를 찾을 수 없습니다. couponId=" + couponId);
        }
        return couponStock;
    }
}
