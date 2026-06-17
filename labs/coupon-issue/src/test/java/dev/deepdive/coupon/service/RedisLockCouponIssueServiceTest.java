package dev.deepdive.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.deepdive.coupon.core.CouponStock;
import dev.deepdive.coupon.support.RedisContainerTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RedisLockCouponIssueServiceTest extends RedisContainerTest {

    private static final Long COUPON_ID = 1L;
    private static final int QUANTITY = 200;
    private static final int REQUEST_COUNT = 200;
    private static final int THREAD_POOL_SIZE = 32;

    @Autowired
    private RedisLockCouponIssueService redisLockCouponIssueService;

    @BeforeEach
    void setUp() {
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
            assertThat(couponStock.remainingQuantity()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }
}
