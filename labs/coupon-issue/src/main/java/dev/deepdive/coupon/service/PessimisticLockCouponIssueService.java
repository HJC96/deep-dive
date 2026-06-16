package dev.deepdive.coupon.service;

import dev.deepdive.coupon.core.CouponStock;
import dev.deepdive.coupon.repository.CouponRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PessimisticLockCouponIssueService {

    private final CouponRepository couponRepository;

    public PessimisticLockCouponIssueService(CouponRepository couponRepository) {
        this.couponRepository = Objects.requireNonNull(couponRepository, "쿠폰 저장소는 필수입니다.");
    }

    @Transactional
    public void issue(Long couponId) {
        CouponStock couponStock = couponRepository.findByIdWithPessimisticLock(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰 재고를 찾을 수 없습니다. couponId=" + couponId));
        couponStock.decrease();
        couponRepository.save(couponStock);
    }
}
