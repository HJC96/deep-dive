package dev.deepdive.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.deepdive.coupon.core.CouponStock;
import dev.deepdive.coupon.repository.CouponRepository;
import dev.deepdive.coupon.repository.InMemoryCouponRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CouponIssueServiceTest {

    private static final Long COUPON_ID = 1L;
    private static final int QUANTITY = 200;

    private CouponRepository couponRepository;
    private CouponIssueService couponIssueService;

    @BeforeEach
    void setUp() {
        couponRepository = new InMemoryCouponRepository();
        couponRepository.save(new CouponStock(COUPON_ID, QUANTITY));
        couponIssueService = new CouponIssueService(couponRepository);
    }

    @Test
    void 동시에_200번_발급하면_재고가_0이_아닐_수_있다() throws Exception {
        int requestCount = 200;
        int threadPoolSize = 32;

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requestCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        try {
            for (int i = 0; i < requestCount; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        couponIssueService.issue(COUPON_ID);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            done.await();

            CouponStock couponStock = couponRepository.findById(COUPON_ID);
            assertThat(couponStock.remainingQuantity()).isNotEqualTo(0);
        } finally {
            executor.shutdownNow();
        }
    }
}
