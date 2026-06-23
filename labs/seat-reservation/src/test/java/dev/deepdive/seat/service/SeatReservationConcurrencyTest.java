package dev.deepdive.seat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.deepdive.seat.core.SeatReservationCommand;
import dev.deepdive.seat.core.SeatReservationRequestResult;
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

/**
 * 좌석 예약 동시성/정합성 케이스를 README 순서대로 한곳에 모은 테스트.
 *
 * <p>위에서 아래로 케이스 1 → 2 → 3 → 4(실패 처리)가 흐른다. 케이스 4의 버스트 부하 비교는
 * datasource를 느린 DB로 바꿔야 해서 {@code BurstLoadSeatReservationTest}로 분리했다.
 *
 * <p>좌석 50개에 좌석마다 5명이 같은 좌석을 노린다 → 요청 250개.
 */
class SeatReservationConcurrencyTest extends KafkaContainerTest {

    private static final int SEATS = 50;
    private static final int USERS_PER_SEAT = 5;
    private static final int REQUEST_COUNT = SEATS * USERS_PER_SEAT; // 250
    private static final int THREAD_POOL_SIZE = 32;
    private static final String DUPLICATE_SEAT_NO = "DUP-1";

    @Autowired
    private UnsafeSeatReservationService unsafeSeatReservationService;

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

    // ── 케이스 1. 동시성 제어 없음 ───────────────────────────────────────────────

    @Test
    void 케이스1_동시성_제어가_없으면_같은_좌석이_여러_명에게_배정된다() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUEST_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try {
            for (int i = 0; i < REQUEST_COUNT; i++) {
                long userId = i + 1L;
                String seatNo = "A-" + (i / USERS_PER_SEAT);   // 같은 좌석 5명을 인접 인덱스로 → 같은 배치에서 동시 실행
                executor.execute(() -> {
                    try {
                        start.await();
                        unsafeSeatReservationService.reserve(userId, seatNo);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            done.await();

            long reservationCount = seatReservationRepository.count();
            System.out.printf("제어 없음 - 좌석 %d개에 예약 %d건 (이중 배정 포함)%n", SEATS, reservationCount);

            // 좌석 수보다 많은 예약 = 같은 좌석이 여러 명에게 배정됨
            assertThat(reservationCount).isGreaterThan(SEATS);
        } finally {
            executor.shutdownNow();
        }
    }

    // ── 케이스 2. Redis 선점 (SADD) ──────────────────────────────────────────────

    @Test
    void 케이스2_좌석마다_여러_명이_몰려도_한_좌석은_한_명만_예약된다() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUEST_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        AtomicInteger acceptedCount = new AtomicInteger();
        AtomicInteger takenCount = new AtomicInteger();

        try {
            for (int i = 0; i < REQUEST_COUNT; i++) {
                long userId = i + 1L;
                String seatNo = "A-" + (i / USERS_PER_SEAT);   // 같은 좌석 5명을 인접 인덱스로 → 같은 배치에서 동시 실행
                executor.execute(() -> {
                    try {
                        start.await();
                        SeatReservationRequestResult result = redisSeatReservationService.reserve(userId, seatNo);
                        switch (result.status()) {
                            case ACCEPTED -> acceptedCount.incrementAndGet();
                            case SEAT_ALREADY_TAKEN -> takenCount.incrementAndGet();
                            default -> {
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            done.await();

            assertThat(acceptedCount.get()).isEqualTo(SEATS);
            assertThat(takenCount.get()).isEqualTo(REQUEST_COUNT - SEATS);
            // 좌석 50개가 정확히 하나씩 예약됨 (이중 배정 없음)
            assertThat(seatReservationRepository.count()).isEqualTo(SEATS);
            for (int seat = 0; seat < SEATS; seat++) {
                assertThat(seatReservationRepository.findAllBySeatNo("A-" + seat)).hasSize(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 케이스2_한_사람이_여러_좌석을_동시에_노려도_한_좌석만_예약된다() throws Exception {
        long userId = 9_999L;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(SEATS);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        AtomicInteger acceptedCount = new AtomicInteger();
        AtomicInteger userBlockedCount = new AtomicInteger();

        try {
            for (int i = 0; i < SEATS; i++) {
                String seatNo = "U-" + i;
                executor.execute(() -> {
                    try {
                        start.await();
                        SeatReservationRequestResult result = redisSeatReservationService.reserve(userId, seatNo);
                        switch (result.status()) {
                            case ACCEPTED -> acceptedCount.incrementAndGet();
                            case USER_ALREADY_HAS_SEAT -> userBlockedCount.incrementAndGet();
                            default -> {
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            done.await();

            assertThat(acceptedCount.get()).isEqualTo(1);
            assertThat(userBlockedCount.get()).isEqualTo(SEATS - 1);
            // 1인 1좌석: 이 사용자의 예약은 정확히 1건
            assertThat(seatReservationRepository.count()).isEqualTo(1);
            assertThat(seatReservationRepository.existsByUserId(userId)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    // ── 케이스 3. Redis + Kafka ──────────────────────────────────────────────────

    @Test
    void 케이스3_선점에_성공한_요청만_kafka로_적재되고_consumer가_좌석마다_한_번만_확정한다() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUEST_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        AtomicInteger acceptedCount = new AtomicInteger();

        try {
            for (int i = 0; i < REQUEST_COUNT; i++) {
                long userId = i + 1L;
                String seatNo = "A-" + (i / USERS_PER_SEAT);   // 같은 좌석 5명을 인접 인덱스로 → 같은 배치에서 동시 실행
                executor.execute(() -> {
                    try {
                        start.await();
                        if (redisKafkaSeatReservationService.reserve(userId, seatNo).accepted()) {
                            acceptedCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            done.await();

            // 선점에 성공한 좌석 50개만 Kafka로 적재됨
            assertThat(acceptedCount.get()).isEqualTo(SEATS);

            // consumer가 비동기로 확정하므로, DB에 좌석 50개가 다 들어올 때까지(최대 15초) 주기적으로 다시 확인한다
            await().atMost(Duration.ofSeconds(15))
                    .untilAsserted(() -> assertThat(seatReservationRepository.count()).isEqualTo(SEATS));
            for (int seat = 0; seat < SEATS; seat++) {
                assertThat(seatReservationRepository.findAllBySeatNo("A-" + seat)).hasSize(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 케이스3_kafka_중복_메시지가_와도_consumer는_같은_좌석을_한_번만_확정한다() {
        String message = new SeatReservationCommand(7_777L, DUPLICATE_SEAT_NO).serialize();
        kafkaTemplate.send(SeatReservationCommandPublisher.TOPIC, DUPLICATE_SEAT_NO, message);
        kafkaTemplate.send(SeatReservationCommandPublisher.TOPIC, DUPLICATE_SEAT_NO, message);

        // consumer가 비동기로 처리하므로, 예약이 1건 생길 때까지(최대 15초) 기다린다
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() ->
                        assertThat(seatReservationRepository.findAllBySeatNo(DUPLICATE_SEAT_NO)).hasSize(1));
        // during: 2초 동안 "계속" 1건인지 확인 — 두 번째(중복) 메시지가 뒤늦게 또 저장하지 않는지 검증
        await().during(Duration.ofSeconds(2))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertThat(seatReservationRepository.findAllBySeatNo(DUPLICATE_SEAT_NO)).hasSize(1));
    }

    // ── 케이스 4. 컨슈머 실패 처리 (FailedEvent) ─────────────────────────────────

    @Test
    void 케이스4_consumer가_db_확정에_실패하면_FailedEvent로_적재된다() {
        // seat_no 컬럼(length=20)을 넘는 30자 좌석 번호 → INSERT 실패를 유발하는 독성 메시지
        String poisonSeatNo = "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"; // X 30개 (컬럼 길이 20 초과)
        String message = new SeatReservationCommand(5_555L, poisonSeatNo).serialize();
        kafkaTemplate.send(SeatReservationCommandPublisher.TOPIC, poisonSeatNo, message);

        // Consumer가 메시지를 비동기로 처리하므로, FailedEvent가 적재될 때까지(최대 15초) 주기적(기본값 100ms)으로 다시 확인한다
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(failedEventRepository.count()).isEqualTo(1));
        assertThat(seatReservationRepository.existsBySeatNo(poisonSeatNo)).isFalse();
    }
}
