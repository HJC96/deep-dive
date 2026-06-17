package dev.deepdive.coupon.service;

import java.util.Objects;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

public class RedissonLockCouponIssueService {

    private static final String LOCK_KEY_PREFIX = "coupon:issue:redisson-lock:";

    private final RedissonClient redissonClient;
    private final CouponIssueService couponIssueService;

    public RedissonLockCouponIssueService(
            RedissonClient redissonClient,
            CouponIssueService couponIssueService
    ) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "RedissonClient는 필수입니다.");
        this.couponIssueService = Objects.requireNonNull(couponIssueService, "쿠폰 발급 서비스는 필수입니다.");
    }

    public void issue(Long couponId) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + Objects.requireNonNull(couponId, "쿠폰 ID는 필수입니다."));

        lock.lock();
        try {
            couponIssueService.issue(couponId);
        } finally {
            lock.unlock();
        }
    }
}
