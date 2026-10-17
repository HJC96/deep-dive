package dev.deepdive.seat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.deepdive.seat.core.FailedEvent;
import dev.deepdive.seat.core.SeatReservation;
import dev.deepdive.seat.core.SeatReservationStatus;
import dev.deepdive.seat.kafka.SeatReservationCommandPublisher;
import dev.deepdive.seat.redis.SeatHoldStore;
import dev.deepdive.seat.store.SeatReservationWriter;
import dev.deepdive.seat.support.KafkaContainerTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * 정합성 보강 케이스: 원자적 선점, DB 유니크 제약, 확정 실패 시 선점 해제, 깨진 메시지 적재.
 *
 * <p>{@code SeatReservationConcurrencyTest}와 같은 컨텍스트 설정이라 Spring 컨텍스트를 재사용한다.
 */
class SeatReservationFailureHandlingTest extends KafkaContainerTest {

    private static final String HELD_SEATS_KEY = "seat:held-seats";
    private static final String APPLIED_USERS_KEY = "seat:applied-users";
    private static final int THREAD_POOL_SIZE = 32;

    @Autowired
    private SeatHoldStore seatHoldStore;

    @Autowired
    private RedisSeatReservationService redisSeatReservationService;

    @Autowired
    private SeatReservationWriter seatReservationWriter;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void clearHolds() {
        flushRedis();
    }

    // ── 원자적 선점 (Lua) ────────────────────────────────────────────────────────

    @Test
    void 이미_잡힌_좌석에서_밀려도_같은_사용자의_빈_좌석_요청은_거절되지_않는다() throws Exception {
        int seats = 32;
        // 예전 구현(SADD 사용자 → SADD 좌석 → 실패 시 SREM 사용자)은 SREM 전 잠깐 "좌석 보유 중"으로 보여
        // 빈 좌석 요청이 USER_ALREADY_HAS_SEAT로 잘못 거절될 수 있었다. 여러 번 반복해 경합 창을 노린다.
        for (int round = 0; round < 20; round++) {
            flushRedis();
            for (int seat = 0; seat < seats - 1; seat++) {
                assertThat(seatHoldStore.hold(100_000L + seat, "T-" + seat)).isEqualTo(SeatReservationStatus.ACCEPTED);
            }
            String freeSeat = "T-" + (seats - 1);
            long userId = 9_999L;

            List<SeatReservationStatus> results = holdConcurrently(userId, seats);

            // 빈 좌석은 반드시 이 사용자에게 돌아가야 한다. (예전 구현은 ACCEPTED 0건으로 끝날 수 있었다)
            // 빈 좌석을 잡은 뒤에 처리된 요청이 USER_ALREADY_HAS_SEAT인 것은 정상이다.
            assertThat(results).containsOnlyOnce(SeatReservationStatus.ACCEPTED);
            assertThat(redisTemplate.opsForSet().isMember(HELD_SEATS_KEY, freeSeat)).isTrue();
            assertThat(redisTemplate.opsForSet().isMember(APPLIED_USERS_KEY, String.valueOf(userId))).isTrue();
        }
    }

    @Test
    void 선점_TTL은_키가_처음_생길_때만_걸리고_이후_선점이_연장하지_않는다() {
        seatHoldStore.hold(1L, "W-1");
        // 시간이 흘러 남은 TTL이 줄어든 상황을 흉내 낸다.
        redisTemplate.expire(HELD_SEATS_KEY, Duration.ofSeconds(10));
        redisTemplate.expire(APPLIED_USERS_KEY, Duration.ofSeconds(10));

        seatHoldStore.hold(2L, "W-2");

        assertThat(redisTemplate.getExpire(HELD_SEATS_KEY)).isBetween(1L, 10L);
        assertThat(redisTemplate.getExpire(APPLIED_USERS_KEY)).isBetween(1L, 10L);
    }

    // ── DB 유니크 제약과 멱등 쓰기 ────────────────────────────────────────────────

    @Test
    void 확정_테이블은_같은_좌석이나_같은_사용자를_두_번_저장하지_못한다() {
        seatReservationRepository.save(new SeatReservation("UK-1", 1L));

        assertThatThrownBy(() -> seatReservationRepository.save(new SeatReservation("UK-1", 2L)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> seatReservationRepository.save(new SeatReservation("UK-2", 1L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_좌석을_동시에_직접_저장해도_한_건만_확정되고_나머지는_이미_예약됨으로_분류된다() throws Exception {
        int writers = 32;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        List<Future<SeatReservationStatus>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < writers; i++) {
                long userId = 50_000L + i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return seatReservationWriter.save(userId, "RACE-1");
                }));
            }
            start.countDown();

            List<SeatReservationStatus> results = new ArrayList<>();
            for (Future<SeatReservationStatus> future : futures) {
                results.add(future.get());   // 중복 키 예외가 새면 여기서 ExecutionException
            }

            assertThat(results).containsOnlyOnce(SeatReservationStatus.ACCEPTED);
            assertThat(results).filteredOn(status -> status == SeatReservationStatus.SEAT_ALREADY_TAKEN)
                    .hasSize(writers - 1);
            assertThat(seatReservationRepository.findAllBySeatNo("RACE-1")).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 같은_사용자_같은_좌석을_다시_저장하면_멱등하게_ACCEPTED를_돌려준다() {
        assertThat(seatReservationWriter.save(7L, "IDEM-1")).isEqualTo(SeatReservationStatus.ACCEPTED);
        assertThat(seatReservationWriter.save(7L, "IDEM-1")).isEqualTo(SeatReservationStatus.ACCEPTED);

        assertThat(seatReservationRepository.findAllBySeatNo("IDEM-1")).hasSize(1);
    }

    // ── 직접 쓰기 경로: DB 결과 반영과 선점 해제 ─────────────────────────────────

    @Test
    void DB에_다른_사람이_확정한_좌석이면_SEAT_ALREADY_TAKEN을_돌려주고_사용자_선점만_푼다() {
        // Redis 선점 윈도우가 비워진 뒤라 Redis는 모르지만 DB에는 이미 확정된 좌석
        seatReservationRepository.save(new SeatReservation("Z-1", 1L));

        assertThat(redisSeatReservationService.reserve(2L, "Z-1").status())
                .isEqualTo(SeatReservationStatus.SEAT_ALREADY_TAKEN);
        assertThat(redisTemplate.opsForSet().isMember(APPLIED_USERS_KEY, "2")).isFalse();
        assertThat(redisTemplate.opsForSet().isMember(HELD_SEATS_KEY, "Z-1")).isTrue();

        // 사용자 선점이 풀렸으므로 다른 좌석은 잡을 수 있다
        assertThat(redisSeatReservationService.reserve(2L, "Z-2").status())
                .isEqualTo(SeatReservationStatus.ACCEPTED);
    }

    @Test
    void DB에_이미_다른_좌석을_가진_사용자면_USER_ALREADY_HAS_SEAT를_돌려주고_좌석_선점만_푼다() {
        seatReservationRepository.save(new SeatReservation("Z-3", 3L));

        assertThat(redisSeatReservationService.reserve(3L, "Z-4").status())
                .isEqualTo(SeatReservationStatus.USER_ALREADY_HAS_SEAT);
        assertThat(redisTemplate.opsForSet().isMember(HELD_SEATS_KEY, "Z-4")).isFalse();

        // 좌석 선점이 풀렸으므로 다른 사용자가 그 좌석을 잡을 수 있다
        assertThat(redisSeatReservationService.reserve(4L, "Z-4").status())
                .isEqualTo(SeatReservationStatus.ACCEPTED);
    }

    @Test
    void DB_확정_중_예외가_나면_Redis_선점을_되돌리고_예외를_던진다() {
        String poisonSeatNo = "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"; // 30자 (seat_no 컬럼 길이 20 초과)

        assertThatThrownBy(() -> redisSeatReservationService.reserve(5L, poisonSeatNo))
                .isInstanceOf(RuntimeException.class);

        assertThat(redisTemplate.opsForSet().isMember(APPLIED_USERS_KEY, "5")).isFalse();
        assertThat(redisTemplate.opsForSet().isMember(HELD_SEATS_KEY, poisonSeatNo)).isFalse();
        // 선점이 풀렸으므로 같은 사용자가 정상 좌석을 다시 시도할 수 있다
        assertThat(redisSeatReservationService.reserve(5L, "Z-5").status())
                .isEqualTo(SeatReservationStatus.ACCEPTED);
    }

    // ── 컨슈머: 파싱 실패도 FailedEvent로 ─────────────────────────────────────────

    @Test
    void 형식이_깨진_메시지도_FailedEvent로_원문과_함께_적재된다() {
        String malformed = "not-a-command";
        kafkaTemplate.send(SeatReservationCommandPublisher.TOPIC, "broken", malformed);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(failedEventRepository.count()).isEqualTo(1));
        FailedEvent failedEvent = failedEventRepository.findAll().get(0);
        assertThat(failedEvent.payload()).isEqualTo(malformed);
        assertThat(failedEvent.userId()).isNull();
        assertThat(failedEvent.seatNo()).isNull();
    }

    private List<SeatReservationStatus> holdConcurrently(long userId, int seats) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<SeatReservationStatus>> futures = new ArrayList<>();
        try {
            for (int seat = 0; seat < seats; seat++) {
                String seatNo = "T-" + seat;
                futures.add(executor.submit(() -> {
                    start.await();
                    return seatHoldStore.hold(userId, seatNo);
                }));
            }
            start.countDown();
            List<SeatReservationStatus> results = new ArrayList<>();
            for (Future<SeatReservationStatus> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
