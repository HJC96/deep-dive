package dev.deepdive.seat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.deepdive.seat.core.SeatReservationCommand;
import dev.deepdive.seat.kafka.SeatReservationCommandPublisher;
import dev.deepdive.seat.support.KafkaContainerTest;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 케이스 4. 버스트 부하: DB가 병목일 때 직접 쓰기와 Kafka 경유의 차이를 비교한다.
 *
 * <p>정합성/실패 처리 케이스(1·2·3·4)는 {@code SeatReservationConcurrencyTest}에 모았지만, 이 버스트 부하 비교만 분리한다.
 * 아래 @DynamicPropertySource가 datasource를 "느린 DB"(풀=2, 타임아웃 250ms, 쓰기 지연)로 바꾸는데,
 * 같은 컨텍스트에서 250개 동시 요청 테스트를 돌리면 풀=2에 막혀 망가지기 때문이다.
 *
 * <p>"처리량이 제한된 느린 DB"를 흉내 내기 위해 커넥션 풀을 2로 줄이고, 커넥션 타임아웃을 짧게,
 * 쓰기마다 지연을 준다. 사용자마다 다른 좌석을 한꺼번에 요청해 다건 쓰기 버스트를 만든다.
 *
 * <ul>
 *   <li>직접 쓰기: 요청 스레드가 느린 DB에 묶여 풀 고갈·커넥션 타임아웃이 발생한다.</li>
 *   <li>Kafka 경유: 요청은 Redis 선점 + publish만 하고 즉시 반환되고, consumer가 천천히 소진한다.</li>
 * </ul>
 */
class BurstLoadSeatReservationTest extends KafkaContainerTest {

    private static final int BURST = 100;

    @DynamicPropertySource
    static void burstProperties(DynamicPropertyRegistry registry) {
        // 처리량이 제한된 느린 DB 흉내
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 2);
        registry.add("spring.datasource.hikari.connection-timeout", () -> 250);
        registry.add("seat.db.write-delay-millis", () -> 20);
    }

    @Autowired
    private RedisSeatReservationService redisSeatReservationService;

    @Autowired
    private RedisKafkaSeatReservationService redisKafkaSeatReservationService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void clearHolds() {
        flushRedis();
    }

    @Test
    void 직접쓰기보다_kafka경유가_빠르게_반환되고_결국_모두_확정된다() throws Exception {
        BurstResult direct = runDirectWriteBurst();   // 직접 쓰기: 요청 스레드가 DB까지 확정
        warmUpKafkaProducer();                          // 첫 publish 메타데이터 비용을 타이밍에서 제거
        BurstResult kafka = runKafkaBurst();          // Kafka 경유: 요청은 publish까지만
        long kafkaConfirmMillis = awaitAllConfirmed("K-", BURST);   // consumer가 전부 확정할 때까지

        System.out.printf("직접 쓰기  - 요청 반환까지 %dms, 성공 %d건, 실패(타임아웃 등) %d건%n",
                direct.elapsedMillis(), direct.accepted(), direct.failed());
        System.out.printf("Kafka 경유 - 요청 반환까지 %dms, 실패 %d건 / DB 전부 확정까지 %dms%n",
                kafka.elapsedMillis(), kafka.failed(), kafkaConfirmMillis);

        // Kafka 경유는 DB에 묶이지 않아 요청이 빠르게 반환되고 실패가 없다
        assertThat(kafka.failed()).isZero();
        assertThat(kafka.elapsedMillis()).isLessThan(direct.elapsedMillis());
        // 직접 쓰기는 느린 DB + 작은 커넥션 풀에 막혀 타임아웃이 발생한다
        assertThat(direct.failed()).isGreaterThan(0);
        // 버스트가 몰려도 Kafka 경유는 결국 좌석 100개를 전부 확정한다
        assertThat(seatReservationRepository.countBySeatNoStartingWith("K-")).isEqualTo((long) BURST);
    }

    // 직접 쓰기: 사용자 1..100이 좌석 D-* 를 동시에 요청 (요청 스레드가 DB까지 확정)
    private BurstResult runDirectWriteBurst() throws InterruptedException {
        return runBurst(1L, "D-", (userId, seatNo) ->
                redisSeatReservationService.reserve(userId, seatNo).accepted());
    }

    // Kafka 경유: 사용자 1001..1100이 좌석 K-* 를 동시에 요청 (요청은 publish까지만)
    private BurstResult runKafkaBurst() throws InterruptedException {
        return runBurst(1001L, "K-", (userId, seatNo) ->
                redisKafkaSeatReservationService.reserve(userId, seatNo).accepted());
    }

    // BURST개 요청을 동시에 시작시키고, 요청이 전부 반환될 때까지 걸린 시간/성공/실패를 잰다
    private BurstResult runBurst(long firstUserId, String seatPrefix, ReserveAction action)
            throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(BURST);
        ExecutorService executor = Executors.newFixedThreadPool(BURST);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        long elapsedMillis;

        try {
            for (int i = 0; i < BURST; i++) {
                long userId = firstUserId + i;
                String seatNo = seatPrefix + i;
                executor.execute(() -> {
                    try {
                        start.await();
                        if (action.reserve(userId, seatNo)) {
                            accepted.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        failed.incrementAndGet();   // 느린 DB + 작은 풀 → 커넥션 타임아웃
                    } finally {
                        done.countDown();
                    }
                });
            }

            long startedAt = System.nanoTime();
            start.countDown();   // 모든 요청을 동시에 출발시킨다
            done.await();
            elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        } finally {
            executor.shutdownNow();
        }
        return new BurstResult(elapsedMillis, accepted.get(), failed.get());
    }

    // 요청은 이미 즉시 반환됐지만 DB 확정은 consumer가 비동기로 천천히 한다.
    // 좌석이 전부 확정될 때까지(최대 30초) 주기적으로 다시 확인하고, 확정에 걸린 시간을 반환한다.
    private long awaitAllConfirmed(String seatPrefix, int expected) {
        long startedAt = System.nanoTime();
        await().atMost(Duration.ofSeconds(30))
                .until(() -> seatReservationRepository.countBySeatNoStartingWith(seatPrefix) == expected);
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    // 첫 publish의 메타데이터 조회 비용이 타이밍에 섞이지 않도록 미리 한 번 보낸다
    private void warmUpKafkaProducer() {
        kafkaTemplate.send(SeatReservationCommandPublisher.TOPIC, "warmup",
                new SeatReservationCommand(999_999L, "WARMUP").serialize());
        kafkaTemplate.flush();
    }

    @FunctionalInterface
    private interface ReserveAction {
        // 예약을 시도하고 ACCEPTED면 true (느린 DB 타임아웃 등은 예외로 던져 호출부가 실패로 집계)
        boolean reserve(long userId, String seatNo) throws Exception;
    }

    private record BurstResult(long elapsedMillis, int accepted, int failed) {
    }
}
