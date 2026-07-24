package dev.deepdive.transaction.msa.seat.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class SeatReservationHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private long requestId;
    private long workshopId;
    private int seatCount;
    private long amount;

    protected SeatReservationHistory() {}

    public SeatReservationHistory(long requestId, long workshopId, int seatCount, long amount) {
        this.requestId = requestId;
        this.workshopId = workshopId;
        this.seatCount = seatCount;
        this.amount = amount;
    }
}
