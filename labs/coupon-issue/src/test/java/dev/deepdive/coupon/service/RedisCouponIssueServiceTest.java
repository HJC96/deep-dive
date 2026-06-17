package dev.deepdive.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.deepdive.coupon.core.CouponStock;
import dev.deepdive.coupon.support.RedisContainerTest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisCouponIssueServiceTest extends RedisContainerTest {

    private static final Long COUPON_ID = 1L;
    private static final String STOCK_KEY = "coupon:stock:1";
    private static final String REDIS_LOCK_KEY = "coupon:issue:lock:1";
    private static final String REDISSON_LOCK_KEY = "coupon:issue:redisson-lock:1";
    private static final int QUANTITY = 200;
    private static final int REQUEST_COUNT = 200;
    private static final int OVER_REQUEST_COUNT = 250;
    private static final int THREAD_POOL_SIZE = 32;

    @Autowired
    private RedisLockCouponIssueService redisLockCouponIssueService;

    @Autowired
    private RedisAtomicCouponIssueService redisAtomicCouponIssueService;

    @Autowired
    private CouponIssueService couponIssueService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private Environment environment;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(List.of(STOCK_KEY, REDIS_LOCK_KEY, REDISSON_LOCK_KEY));
        redisTemplate.opsForValue().set(STOCK_KEY, String.valueOf(QUANTITY));
        couponRepository.save(new CouponStock(COUPON_ID, QUANTITY));
    }

    @Test
    void redis_분산락으로_동시에_200번_발급하면_재고가_0이_된다() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUEST_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try {
            for (int i = 0; i < REQUEST_COUNT; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        redisLockCouponIssueService.issue(COUPON_ID);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            long startedAt = System.nanoTime();
            start.countDown();
            done.await();
            System.out.printf("Redis 분산락: %.3fms%n", (System.nanoTime() - startedAt) / 1_000_000.0);

            CouponStock couponStock = couponRepository.findById(COUPON_ID).orElseThrow();
            assertThat(couponStock.remainingQuantity()).isEqualTo(0);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void redis_lua로_재고보다_많이_요청해도_재고가_음수가_되지_않는다() throws Exception {
        AtomicInteger issuedCount = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(OVER_REQUEST_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try {
            for (int i = 0; i < OVER_REQUEST_COUNT; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        if (redisAtomicCouponIssueService.issue(COUPON_ID)) {
                            issuedCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            long startedAt = System.nanoTime();
            start.countDown();
            done.await();
            System.out.printf("Redis Lua 원자 차감: %.3fms%n", (System.nanoTime() - startedAt) / 1_000_000.0);

            assertThat(issuedCount.get()).isEqualTo(QUANTITY);
            assertThat(redisTemplate.opsForValue().get(STOCK_KEY)).isEqualTo("0");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void redisson_분산락으로_동시에_200번_발급하면_재고가_0이_된다() throws Exception {
        RedissonClient redissonClient = Redisson.create(redissonConfig());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUEST_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try {
            RedissonLockCouponIssueService redissonLockCouponIssueService =
                    new RedissonLockCouponIssueService(redissonClient, couponIssueService);

            for (int i = 0; i < REQUEST_COUNT; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        redissonLockCouponIssueService.issue(COUPON_ID);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            long startedAt = System.nanoTime();
            start.countDown();
            done.await();
            System.out.printf("Redisson 분산락: %.3fms%n", (System.nanoTime() - startedAt) / 1_000_000.0);

            CouponStock couponStock = couponRepository.findById(COUPON_ID).orElseThrow();
            assertThat(couponStock.remainingQuantity()).isEqualTo(0);
        } finally {
            executor.shutdownNow();
            redissonClient.shutdown();
        }
    }

    private Config redissonConfig() {
        String host = environment.getRequiredProperty("spring.data.redis.host");
        int port = environment.getRequiredProperty("spring.data.redis.port", Integer.class);

        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port);
        return config;
    }
}
