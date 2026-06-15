package dev.deepdive.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.deepdive.coupon.core.CouponStock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import dev.deepdive.coupon.support.MySQLContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CouponIssueServiceTest extends MySQLContainerTest {

    private static final Long COUPON_ID = 1L;
    private static final Long WARMUP_COUPON_ID = 9_999L;
    private static final int QUANTITY = 200;
    private static final int REQUEST_COUNT = 200;
    private static final int THREAD_POOL_SIZE = 32;
    private static final int WARMUP_ITERATIONS = 1_000;

    // 콜드 스타트(JIT, 커넥션 풀 채우기, MySQL 버퍼풀/플랜 캐시)는 JVM당 한 번만 흡수하면 된다.
    private static boolean warmedUp = false;

    // @Transactional이 프록시를 통해 동작하도록 Spring 빈으로 주입받는다.
    @Autowired
    private CouponIssueService couponIssueService;

    private SynchronizedCouponIssueService synchronizedCouponIssueService;
    private AtomicUpdateCouponIssueService atomicUpdateCouponIssueService;

    @BeforeEach
    void setUp() throws Exception {
        warmUpOnce();
        couponRepository.save(new CouponStock(COUPON_ID, QUANTITY));
        synchronizedCouponIssueService = new SynchronizedCouponIssueService(couponRepository);
        atomicUpdateCouponIssueService = new AtomicUpdateCouponIssueService(couponRepository);
    }

    @Test
    void 동시에_200번_발급하면_재고가_0이_아닐_수_있다() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUEST_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try {
            for (int i = 0; i < REQUEST_COUNT; i++) {
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

            long startedAt = System.nanoTime();
            start.countDown();
            done.await();
            System.out.printf("락 없음: %.3fms%n", (System.nanoTime() - startedAt) / 1_000_000.0);

            CouponStock couponStock = couponRepository.findById(COUPON_ID).orElseThrow();
            assertThat(couponStock.remainingQuantity()).isNotEqualTo(0);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void synchronized로_동시에_200번_발급하면_재고가_0이_된다() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUEST_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try {
            for (int i = 0; i < REQUEST_COUNT; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        synchronizedCouponIssueService.issue(COUPON_ID);
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
            System.out.printf("synchronized: %.3fms%n", (System.nanoTime() - startedAt) / 1_000_000.0);

            CouponStock couponStock = couponRepository.findById(COUPON_ID).orElseThrow();
            assertThat(couponStock.remainingQuantity()).isEqualTo(0);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 원자적_update로_동시에_200번_발급하면_재고만큼만_성공한다() throws Exception {
        AtomicInteger issuedCount = new AtomicInteger();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUEST_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try {
            for (int i = 0; i < REQUEST_COUNT; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        if (atomicUpdateCouponIssueService.issue(COUPON_ID)) {
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
            System.out.printf("원자적 UPDATE: %.3fms%n", (System.nanoTime() - startedAt) / 1_000_000.0);

            assertThat(issuedCount.get()).isEqualTo(QUANTITY);
            assertThat(couponRepository.findById(COUPON_ID).orElseThrow().remainingQuantity()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    // 측정 전에 발급 경로(findById+save, 원자적 UPDATE)를 미리 실행해 콜드 스타트를 걷어낸다.
    private void warmUpOnce() throws Exception {
        if (warmedUp) {
            return;
        }
        couponRepository.save(new CouponStock(WARMUP_COUPON_ID, Integer.MAX_VALUE));
        AtomicUpdateCouponIssueService atomicWarmup = new AtomicUpdateCouponIssueService(couponRepository);

        measureIssueTimeMillis(WARMUP_ITERATIONS, THREAD_POOL_SIZE, () -> couponIssueService.issue(WARMUP_COUPON_ID));
        measureIssueTimeMillis(WARMUP_ITERATIONS, THREAD_POOL_SIZE, () -> atomicWarmup.issue(WARMUP_COUPON_ID));

        warmedUp = true;
    }
}
