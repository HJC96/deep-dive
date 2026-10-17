package dev.deepdive.coupon.service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisLockCouponIssueService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final long LOCK_RETRY_MILLIS = 10L;
    // 값이 내 토큰일 때만 지운다. GET과 DEL 사이에 다른 명령이 끼지 않도록 Lua로 묶는다.
    private static final RedisScript<Long> UNLOCK_SCRIPT = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

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

        String token = lock(lockKey);
        try {
            couponIssueService.issue(couponId);
        } finally {
            unlock(lockKey, token);
        }
    }

    // 요청마다 고유 토큰(UUID)을 락 값으로 쓴다.
    // 고정 값("LOCK")이면 TTL이 지나 다른 요청이 잡은 락을 내 DEL이 지워버릴 수 있다.
    String lock(String lockKey) {
        String token = UUID.randomUUID().toString();
        while (true) {
            if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL))) {
                return token;
            }
            sleep();
        }
    }

    // 내 토큰일 때만 삭제한다. 이미 만료되어 남의 락이 되었으면 false.
    boolean unlock(String lockKey, String token) {
        Long deleted = redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), token);
        return Long.valueOf(1L).equals(deleted);
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
