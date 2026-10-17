package dev.deepdive.seat.service;

import dev.deepdive.seat.core.SeatReservationRequestResult;
import dev.deepdive.seat.core.SeatReservationStatus;
import dev.deepdive.seat.kafka.SeatReservationCommandPublisher;
import dev.deepdive.seat.redis.SeatHoldStore;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Redis로 좌석/사용자를 선점하고, 선점에 성공한 요청만 확정 커맨드를 Kafka에 적재한 뒤 즉시 반환한다.
 *
 * <p>DB 확정은 요청 경로에서 분리되어 {@code SeatReservationConfirmConsumer}가 비동기로 처리한다.
 * 요청이 한꺼번에 몰려도 요청 경로는 Redis 선점 + Kafka publish만 하므로 DB 속도에 묶이지 않고,
 * 몰린 쓰기는 토픽이 버퍼처럼 받아 둔다.
 *
 * <p>{@code ACCEPTED}는 "접수됨"이다. 전송은 비동기라 반환 뒤에 실패할 수 있고, 그때는 콜백이 선점을 해제한다.
 * 클라이언트에 최종 결과까지 보장하려면 아웃박스(같은 트랜잭션에 이벤트 저장 후 릴레이)나 결과 조회 API가 필요하다.
 */
@Service
public class RedisKafkaSeatReservationService {

    private static final Logger log = LoggerFactory.getLogger(RedisKafkaSeatReservationService.class);

    private final SeatHoldStore seatHoldStore;
    private final SeatReservationCommandPublisher commandPublisher;

    public RedisKafkaSeatReservationService(
            SeatHoldStore seatHoldStore,
            SeatReservationCommandPublisher commandPublisher
    ) {
        this.seatHoldStore = Objects.requireNonNull(seatHoldStore, "좌석 선점 저장소는 필수입니다.");
        this.commandPublisher = Objects.requireNonNull(commandPublisher, "예약 커맨드 프로듀서는 필수입니다.");
    }

    public SeatReservationRequestResult reserve(long userId, String seatNo) {
        SeatReservationStatus status = seatHoldStore.hold(userId, seatNo);
        if (status != SeatReservationStatus.ACCEPTED) {
            return new SeatReservationRequestResult(userId, seatNo, status);
        }

        CompletableFuture<?> sent;
        try {
            sent = commandPublisher.publish(userId, seatNo);
        } catch (RuntimeException e) {
            // 직렬화 실패, 메타데이터 대기 초과처럼 send()가 즉시 던지는 경우
            seatHoldStore.release(userId, seatNo);
            throw e;
        }
        // 전송은 비동기라 결과를 기다리지 않고 콜백으로 보상한다. (요청 경로는 막지 않는다)
        // 브로커 전송이 최종 실패하면 컨슈머가 영영 확정하지 않으므로 선점을 풀어 좌석이 붕 뜨지 않게 한다.
        sent.whenComplete((result, failure) -> {
            if (failure != null) {
                log.error("예약 커맨드 전송 실패, 선점을 해제합니다. userId={}, seatNo={}", userId, seatNo, failure);
                seatHoldStore.release(userId, seatNo);
            }
        });
        return new SeatReservationRequestResult(userId, seatNo, SeatReservationStatus.ACCEPTED);
    }
}
