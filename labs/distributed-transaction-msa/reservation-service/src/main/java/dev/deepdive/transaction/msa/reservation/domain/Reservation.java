package dev.deepdive.transaction.msa.reservation.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private long userId;
    private long workshopId;
    private int seatCount;
    private long amount;
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    protected Reservation() {}

    public Reservation(long userId, long workshopId, int seatCount) {
        this.userId = userId;
        this.workshopId = workshopId;
        this.seatCount = seatCount;
        this.status = ReservationStatus.CREATED;
    }

    public void confirm(long amount) {
        this.amount = amount;
        this.status = ReservationStatus.CONFIRMED;
    }

    public Long getId() { return id; }
    public long getUserId() { return userId; }
    public long getWorkshopId() { return workshopId; }
    public int getSeatCount() { return seatCount; }
    public long getAmount() { return amount; }
    public ReservationStatus getStatus() { return status; }
}
