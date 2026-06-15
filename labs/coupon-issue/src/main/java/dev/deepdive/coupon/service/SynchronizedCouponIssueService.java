package dev.deepdive.coupon.service;

import dev.deepdive.coupon.core.CouponStock;
import dev.deepdive.coupon.repository.CouponRepository;
import java.util.Objects;

public final class SynchronizedCouponIssueService {

    private final CouponRepository couponRepository;

    public SynchronizedCouponIssueService(CouponRepository couponRepository) {
        this.couponRepository = Objects.requireNonNull(couponRepository, "쿠폰 저장소는 필수입니다.");
    }

    public synchronized void issue(Long couponId) {
        CouponStock couponStock = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰 재고를 찾을 수 없습니다. couponId=" + couponId));
        couponStock.decrease();
        couponRepository.save(couponStock);
    }
}
