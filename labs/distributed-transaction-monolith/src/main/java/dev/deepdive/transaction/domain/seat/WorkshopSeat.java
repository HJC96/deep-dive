package dev.deepdive.transaction.domain.seat;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class WorkshopSeat {
    @Id
    private Long workshopId;
    private String title;
    private int capacity;
    private int reservedCount;
    private long pricePerSeat;

    protected WorkshopSeat() {}

    public WorkshopSeat(long workshopId, String title, int capacity, long pricePerSeat) {
        if (capacity <= 0 || pricePerSeat <= 0) throw new IllegalArgumentException("capacity and price must be positive");
        this.workshopId = workshopId;
        this.title = title;
        this.capacity = capacity;
        this.pricePerSeat = pricePerSeat;
    }

    public long reserve(int seatCount) {
        if (seatCount <= 0) throw new IllegalArgumentException("seatCount must be positive");
        if (reservedCount + seatCount > capacity) throw new NotEnoughSeatsException();
        reservedCount += seatCount;
        return Math.multiplyExact(pricePerSeat, seatCount);
    }

    public int getReservedCount() { return reservedCount; }
}
