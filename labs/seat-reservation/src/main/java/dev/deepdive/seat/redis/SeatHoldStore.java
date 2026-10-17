package dev.deepdive.seat.redis;

import dev.deepdive.seat.core.SeatReservationStatus;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis Set으로 좌석을 선점한다. 두 불변식을 "유니크 멤버십"으로 보장한다.
 *
 * <ul>
 *   <li>한 사람은 한 좌석만 선점한다 — 좌석을 가진 사용자 집합({@code seat:applied-users}).</li>
 *   <li>한 좌석은 한 명만 선점한다 — 잡힌 좌석 집합({@code seat:held-seats}).</li>
 * </ul>
 *
 * <p><b>원자성</b>: 사용자 확인 → 좌석 확인 → 두 집합에 {@code SADD}를 Lua 스크립트 하나로 실행한다.
 * 예전처럼 {@code SADD 사용자} → {@code SADD 좌석} → (실패 시) {@code SREM 사용자}를 따로 보내면,
 * 좌석에서 밀린 사용자가 SREM으로 빠지기 전 잠깐 동안 "좌석 보유 중"으로 보여
 * 같은 사용자의 다른 좌석 요청이 잘못 거절될 수 있었다. 스크립트는 둘 다 비어 있을 때만 넣으므로
 * 되돌리기(SREM)가 필요 없다.
 *
 * <p><b>TTL 선택</b>: Redis Set은 멤버 단위 TTL이 없어 키 전체({@code seat:held-seats}·
 * {@code seat:applied-users})에만 만료를 걸 수 있다. 예전에는 새 멤버가 들어올 때마다 키 TTL을 다시 걸어
 * 요청이 계속 들어오면 집합이 영영 만료되지 않았다(고아 선점이 쌓임). 지금은 {@code EXPIRE ... NX}로
 * <b>키가 처음 생길 때만</b> TTL을 건다 → 첫 선점부터 {@code hold-ttl-seconds}짜리 고정 "선점 윈도우".
 * 대가로 윈도우가 끝나면 최근 선점까지 통째로 비워지는데, 그 순간의 이중 배정은 DB 유니크 제약이 막는다.
 * 좌석별 정밀 TTL이 필요하면 키 1개=좌석 1개인 {@code SET NX EX} 방식이 맞다.
 */
@Component
public class SeatHoldStore {

    private static final String HELD_SEATS_KEY = "seat:held-seats";
    private static final String APPLIED_USERS_KEY = "seat:applied-users";

    private static final long RESULT_ACCEPTED = 0L;
    private static final long RESULT_USER_ALREADY_HAS_SEAT = 1L;
    private static final long RESULT_SEAT_ALREADY_TAKEN = 2L;

    // KEYS[1]=사용자 집합, KEYS[2]=좌석 집합, ARGV[1]=userId, ARGV[2]=seatNo, ARGV[3]=TTL(초)
    private static final RedisScript<Long> HOLD_SCRIPT = RedisScript.of("""
            if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 1 then
                return 1
            end
            if redis.call('SISMEMBER', KEYS[2], ARGV[2]) == 1 then
                return 2
            end
            redis.call('SADD', KEYS[1], ARGV[1])
            redis.call('SADD', KEYS[2], ARGV[2])
            redis.call('EXPIRE', KEYS[1], ARGV[3], 'NX')
            redis.call('EXPIRE', KEYS[2], ARGV[3], 'NX')
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long holdTtlSeconds;

    public SeatHoldStore(
            StringRedisTemplate redisTemplate,
            @Value("${seat.redis.hold-ttl-seconds:300}") long holdTtlSeconds
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "RedisTemplate은 필수입니다.");
        if (holdTtlSeconds <= 0) {
            throw new IllegalArgumentException("선점 TTL은 0보다 커야 합니다.");
        }
        this.holdTtlSeconds = holdTtlSeconds;
    }

    public SeatReservationStatus hold(long userId, String seatNo) {
        Long result = redisTemplate.execute(
                HOLD_SCRIPT,
                List.of(APPLIED_USERS_KEY, HELD_SEATS_KEY),
                String.valueOf(userId), Objects.requireNonNull(seatNo, "좌석 번호는 필수입니다."), String.valueOf(holdTtlSeconds));
        if (result == null) {
            throw new IllegalStateException("좌석 선점 스크립트 결과가 없습니다.");
        }
        if (result == RESULT_ACCEPTED) {
            return SeatReservationStatus.ACCEPTED;
        }
        if (result == RESULT_USER_ALREADY_HAS_SEAT) {
            return SeatReservationStatus.USER_ALREADY_HAS_SEAT;
        }
        if (result == RESULT_SEAT_ALREADY_TAKEN) {
            return SeatReservationStatus.SEAT_ALREADY_TAKEN;
        }
        throw new IllegalStateException("알 수 없는 좌석 선점 결과입니다. result=" + result);
    }

    // 선점을 통째로 되돌린다. (DB 확정 예외, Kafka 전송 실패처럼 이 선점이 확정될 수 없을 때)
    public void release(long userId, String seatNo) {
        releaseUser(userId);
        releaseSeat(seatNo);
    }

    // 사용자만 되돌린다. (DB에 이미 다른 사람이 이 좌석을 확정해 둔 경우 — 좌석은 실제로 잡혀 있다)
    public void releaseUser(long userId) {
        redisTemplate.opsForSet().remove(APPLIED_USERS_KEY, String.valueOf(userId));
    }

    // 좌석만 되돌린다. (DB에 이 사용자가 이미 다른 좌석을 확정해 둔 경우 — 사용자는 실제로 좌석이 있다)
    public void releaseSeat(String seatNo) {
        redisTemplate.opsForSet().remove(HELD_SEATS_KEY, seatNo);
    }
}
