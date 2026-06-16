package dev.deepdive.coupon.service;

import dev.deepdive.coupon.core.CouponStock;
import dev.deepdive.coupon.repository.CouponRepository;
import dev.deepdive.coupon.repository.NamedLockRepository;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NamedLockCouponIssueService {

    private final NamedLockRepository namedLockRepository;
    private final CouponRepository couponRepository;
    // issueOnce의 REQUIRES_NEW가 프록시를 통해 적용되도록 자기 자신(프록시)을 주입받는다.
    private final NamedLockCouponIssueService self;

    public NamedLockCouponIssueService(
            NamedLockRepository namedLockRepository,
            CouponRepository couponRepository,
            @Lazy NamedLockCouponIssueService self) {
        this.namedLockRepository = Objects.requireNonNull(namedLockRepository, "락 저장소는 필수입니다.");
        this.couponRepository = Objects.requireNonNull(couponRepository, "쿠폰 저장소는 필수입니다.");
        this.self = self;
    }

    // @Transactional로 getLock/releaseLock을 같은 커넥션에 고정한다.
    @Transactional
    public void issue(Long couponId) {
        try {
            namedLockRepository.getLock(couponId.toString());
            self.issueOnce(couponId);
        } finally {
            namedLockRepository.releaseLock(couponId.toString());
        }
    }

    // REQUIRES_NEW로 차감을 별도 트랜잭션에서 처리한다.
    // 락을 풀기 전에 차감이 커밋되어야 다음 스레드가 갱신된 재고를 읽는다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void issueOnce(Long couponId) {
        CouponStock couponStock = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰 재고를 찾을 수 없습니다. couponId=" + couponId));
        couponStock.decrease();
        couponRepository.save(couponStock);
    }
}
