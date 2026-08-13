package dev.deepdive.transaction.tcc;

/**
 * 참여자가 자기 요청을 어디까지 처리했는지 적어 두는 상태값.
 *
 * <p>락을 쥐지 않는 대신 멱등 처리와 빈 취소 판단을 참여자가 직접 해야 하는데, 그 판단의 근거가 이 값이다.
 * 좌석과 지갑이 서로 다른 데이터베이스에 있어 로그 테이블은 둘로 나뉘지만 상태값의 의미는 같다.
 */
public final class TccState {

    public static final String TRIED = "TRIED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String CANCELLED = "CANCELLED";

    private TccState() {
    }
}
