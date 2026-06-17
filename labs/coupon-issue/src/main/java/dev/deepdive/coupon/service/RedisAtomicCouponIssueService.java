package dev.deepdive.coupon.service;

import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisAtomicCouponIssueService {

    private static final String STOCK_KEY_PREFIX = "coupon:stock:";
    private static final RedisScript<Long> DECREASE_STOCK_SCRIPT = RedisScript.of("""
            local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
            if stock < 1 then
                return 0
            end
            redis.call('DECR', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisAtomicCouponIssueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "RedisTemplate은 필수입니다.");
    }

    public boolean issue(Long couponId) {
        Long result = redisTemplate.execute(DECREASE_STOCK_SCRIPT, List.of(stockKey(couponId)));
        return Long.valueOf(1L).equals(result);
    }

    private String stockKey(Long couponId) {
        return STOCK_KEY_PREFIX + Objects.requireNonNull(couponId, "쿠폰 ID는 필수입니다.");
    }
}
