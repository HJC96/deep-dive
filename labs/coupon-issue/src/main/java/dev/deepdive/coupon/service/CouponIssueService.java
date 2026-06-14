package dev.deepdive.coupon.service;

import dev.deepdive.coupon.core.CouponStock;
import dev.deepdive.coupon.repository.CouponRepository;
import java.util.Objects;

public final class CouponIssueService {

    private final CouponRepository couponRepository;

    public CouponIssueService(CouponRepository couponRepository) {
        this.couponRepository = Objects.requireNonNull(couponRepository, "쿠폰 저장소는 필수입니다.");
    }

    public void issue(Long couponId) {
        CouponStock couponStock = couponRepository.findById(couponId);
        couponStock.decrease();
    }
}
