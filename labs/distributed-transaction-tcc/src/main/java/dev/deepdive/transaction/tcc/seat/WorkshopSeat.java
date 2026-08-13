package dev.deepdive.transaction.tcc.seat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 좌석. 확정된 좌석({@code reservedCount})과 잡아 둔 좌석({@code heldCount})을 칸으로 나눠 둔다.
 *
 * <p>"아직 확정은 아니지만 남이 가져가면 안 되는" 상태를 데이터로 표현해야 락 없이 버틸 수 있다.
 * 잡아 둔 좌석은 Try에서 이미 커밋되어 남에게도 보이므로, 정원 검사가 두 칸을 함께 세면 두 요청이
 * 같은 좌석을 나눠 갖지 않는다.
 */
@Entity
@Table(name = "workshop_seat")
public class WorkshopSeat {

    @Id
    private Long id;

    private String name;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(nullable = false)
    private long price;

    @Column(name = "reserved_count", nullable = false)
    private int reservedCount;

    @Column(name = "held_count", nullable = false)
    private int heldCount;

    protected WorkshopSeat() {
    }

    public WorkshopSeat(Long id, String name, int totalSeats, long price) {
        this.id = id;
        this.name = name;
        this.totalSeats = totalSeats;
        this.price = price;
        this.reservedCount = 0;
        this.heldCount = 0;
    }

    /**
     * 좌석을 잡아 둔다. 남은 자리가 모자라면 false.
     *
     * <p>확정된 좌석뿐 아니라 남이 잡아 둔 좌석까지 빼고 세야 한다. 잡아 둔 좌석도 이미 커밋되어
     * 남에게 보이는 값이라, 이 뺄셈이 곧 락을 대신한다.
     */
    public boolean hold(int seatCount) {
        if (reservedCount + heldCount + seatCount > totalSeats) {
            return false;
        }
        heldCount += seatCount;
        return true;
    }

    /** 잡아 둔 좌석을 확정으로 옮긴다. */
    public void confirmHold(int seatCount) {
        heldCount -= seatCount;
        reservedCount += seatCount;
    }

    /**
     * 잡아 둔 좌석을 되돌린다.
     *
     * <p>데이터베이스가 롤백해 주는 게 아니라 반대 방향으로 빼는 것이다. 2PC의 {@code XA ROLLBACK}과
     * 갈리는 지점이 여기다.
     */
    public void releaseHold(int seatCount) {
        heldCount -= seatCount;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public long getPrice() {
        return price;
    }

    public int getReservedCount() {
        return reservedCount;
    }

    public int getHeldCount() {
        return heldCount;
    }
}
