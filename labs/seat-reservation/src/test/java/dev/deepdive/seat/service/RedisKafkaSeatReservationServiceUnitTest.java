package dev.deepdive.seat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepdive.seat.core.SeatReservationStatus;
import dev.deepdive.seat.kafka.SeatReservationCommandPublisher;
import dev.deepdive.seat.redis.SeatHoldStore;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

// Kafka 전송 실패를 브로커 없이 재현하는 단위 테스트.
class RedisKafkaSeatReservationServiceUnitTest {

    private final SeatHoldStore seatHoldStore = mock(SeatHoldStore.class);
    private final SeatReservationCommandPublisher publisher = mock(SeatReservationCommandPublisher.class);
    private final RedisKafkaSeatReservationService service =
            new RedisKafkaSeatReservationService(seatHoldStore, publisher);

    @Test
    void 전송이_비동기로_실패하면_선점을_해제한다() {
        CompletableFuture<Object> sent = new CompletableFuture<>();
        when(seatHoldStore.hold(1L, "A-1")).thenReturn(SeatReservationStatus.ACCEPTED);
        doReturn(sent).when(publisher).publish(1L, "A-1");

        // 요청 경로는 전송 결과를 기다리지 않고 바로 반환한다
        assertThat(service.reserve(1L, "A-1").accepted()).isTrue();
        verify(seatHoldStore, never()).release(anyLong(), anyString());

        sent.completeExceptionally(new IllegalStateException("broker down"));

        verify(seatHoldStore).release(1L, "A-1");
    }

    @Test
    void 전송이_성공하면_선점을_유지한다() {
        CompletableFuture<Object> sent = new CompletableFuture<>();
        when(seatHoldStore.hold(1L, "A-1")).thenReturn(SeatReservationStatus.ACCEPTED);
        doReturn(sent).when(publisher).publish(1L, "A-1");

        service.reserve(1L, "A-1");
        sent.complete(new Object());

        verify(seatHoldStore, never()).release(anyLong(), anyString());
    }

    @Test
    void send가_즉시_예외를_던지면_선점을_해제하고_예외를_전파한다() {
        when(seatHoldStore.hold(1L, "A-1")).thenReturn(SeatReservationStatus.ACCEPTED);
        when(publisher.publish(1L, "A-1")).thenThrow(new IllegalStateException("metadata timeout"));

        assertThatThrownBy(() -> service.reserve(1L, "A-1")).isInstanceOf(IllegalStateException.class);

        verify(seatHoldStore).release(1L, "A-1");
    }
}
