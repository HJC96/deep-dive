package dev.deepdive.seat.core;

import java.util.Objects;

/**
 * Kafka 토픽에 적재하는 예약 확정 커맨드.
 *
 * <p>좌석 번호({@code A-10})에는 {@code |}가 없으므로 {@code userId|seatNo} 형태로 직렬화한다.
 */
public record SeatReservationCommand(long userId, String seatNo) {

    private static final String SEPARATOR = "|";

    public SeatReservationCommand {
        Objects.requireNonNull(seatNo, "좌석 번호는 필수입니다.");
    }

    public String serialize() {
        return userId + SEPARATOR + seatNo;
    }

    public static SeatReservationCommand parse(String raw) {
        int separatorIndex = raw.indexOf(SEPARATOR);
        if (separatorIndex < 0) {
            throw new IllegalArgumentException("잘못된 커맨드 형식입니다. raw=" + raw);
        }
        long userId = Long.parseLong(raw.substring(0, separatorIndex));
        String seatNo = raw.substring(separatorIndex + 1);
        return new SeatReservationCommand(userId, seatNo);
    }
}
