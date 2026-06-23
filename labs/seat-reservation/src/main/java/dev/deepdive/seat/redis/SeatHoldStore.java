package dev.deepdive.seat.redis;

import dev.deepdive.seat.core.SeatReservationStatus;
import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Set({@code SADD})으로 좌석을 선점한다. 두 불변식을 "유니크 멤버십"으로 보장한다.
 *
 * <ul>
 *   <li>한 사람은 한 좌석만 선점한다 — 좌석을 가진 사용자 집합({@code seat:applied-users}).</li>
 *   <li>한 좌석은 한 명만 선점한다 — 잡힌 좌석 집합({@code seat:held-seats}).</li>
 * </ul>
 *
 * <p>{@code SADD}는 새로 추가됐을 때만 1을 반환하므로, 1이면 그 멤버를 처음 넣은 요청 = 선점 성공이다.
 * 사용자를 먼저 집합에 넣고 좌석을 넣되, 좌석 선점에 실패하면 넣어 둔 사용자를 {@code SREM}으로 빼
 * 밀린 사용자가 다른 좌석을 다시 시도하게 한다. (강의 8강의 {@code SADD applied-users}와 같은 결)
 *
 * <p><b>TTL 주의</b>: Redis Set은 멤버 단위 TTL이 없어 좌석/사용자 하나하나에 만료를 걸 수 없고,
 * 키 전체({@code seat:held-seats}·{@code seat:applied-users})에만 걸 수 있다. 그래서 여기서는
 * "선점 윈도우"처럼 키 레벨 TTL을 둔다 — 선점 후 확정 전에 앱이 죽어도 집합이 영원히 남지 않고
 * {@code hold-ttl-seconds} 뒤 통째로 비워진다. 좌석별 정밀 TTL이 필요하면 키 1개=좌석 1개인
 * {@code SET NX EX} 방식이 맞다.
 */
@Component
public class SeatHoldStore {

    private static final String HELD_SEATS_KEY = "seat:held-seats";
    private static final String APPLIED_USERS_KEY = "seat:applied-users";

    private final StringRedisTemplate redisTemplate;
    private final Duration holdTtl;

    public SeatHoldStore(
            StringRedisTemplate redisTemplate,
            @Value("${seat.redis.hold-ttl-seconds:300}") long holdTtlSeconds
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "RedisTemplate은 필수입니다.");
        this.holdTtl = Duration.ofSeconds(holdTtlSeconds);
    }

    public SeatReservationStatus hold(long userId, String seatNo) {
        // 1인 1좌석: 사용자를 집합에 추가, 이미 있으면 좌석 보유 중
        if (!added(APPLIED_USERS_KEY, String.valueOf(userId))) {
            return SeatReservationStatus.USER_ALREADY_HAS_SEAT;
        }
        // 이중 배정 방지: 좌석을 집합에 추가, 이미 있으면 선점됨
        if (!added(HELD_SEATS_KEY, seatNo)) {
            redisTemplate.opsForSet().remove(APPLIED_USERS_KEY, String.valueOf(userId));   // 좌석 밀림 → 사용자 되돌림
            return SeatReservationStatus.SEAT_ALREADY_TAKEN;
        }
        return SeatReservationStatus.ACCEPTED;
    }

    // SADD 결과가 1이면 집합에 새로 추가된 것 = 그 멤버를 처음 넣은 요청 = 선점 성공
    private boolean added(String setKey, String member) {
        Long addedCount = redisTemplate.opsForSet().add(setKey, member);
        boolean isNew = addedCount != null && addedCount == 1L;
        if (isNew) {
            // 선점 집합이 영원히 남지 않도록 키 레벨 TTL을 갱신한다.
            // (멤버 단위 TTL은 Set에 없어 키 전체에만 건다 — 클래스 주석의 TTL 주의 참고)
            redisTemplate.expire(setKey, holdTtl);
        }
        return isNew;
    }
}
