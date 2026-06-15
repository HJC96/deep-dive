package dev.deepdive.coupon.service;

import dev.deepdive.coupon.repository.CouponRepository;
import java.util.Objects;

public final class AtomicUpdateCouponIssueService {

    private final CouponRepository couponRepository;

    public AtomicUpdateCouponIssueService(CouponRepository couponRepository) {
        this.couponRepository = Objects.requireNonNull(couponRepository, "쿠폰 저장소는 필수입니다.");
    }

    public boolean issue(Long couponId) {
        return couponRepository.decreaseQuantity(couponId) == 1;
    }
}
