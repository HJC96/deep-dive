package dev.deepdive.coupon.service;

import java.time.Duration;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisLockCouponIssueService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final long LOCK_RETRY_MILLIS = 10L;
    private static final String LOCK_VALUE = "LOCK";

    private final CouponIssueService couponIssueService;
    private final StringRedisTemplate redisTemplate;

    public RedisLockCouponIssueService(
            CouponIssueService couponIssueService,
            StringRedisTemplate redisTemplate
    ) {
        this.couponIssueService = Objects.requireNonNull(couponIssueService, "쿠폰 발급 서비스는 필수입니다.");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "RedisTemplate은 필수입니다.");
    }

    public void issue(Long couponId) {
        String lockKey = "coupon:issue:lock:" + couponId;

        lock(lockKey);
        try {
            couponIssueService.issue(couponId);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private void lock(String lockKey) {
        while (true) {
            if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, LOCK_VALUE, LOCK_TTL))) {
                return;
            }
            sleep();
        }
    }

    private void sleep() {
        try {
            Thread.sleep(LOCK_RETRY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Redis 락 대기 중 인터럽트가 발생했습니다.", e);
        }
    }
}
