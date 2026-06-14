package dev.deepdive.coupon.repository;

import dev.deepdive.coupon.core.CouponStock;

public interface CouponRepository {

    void save(CouponStock couponStock);

    CouponStock findById(Long couponId);
}
