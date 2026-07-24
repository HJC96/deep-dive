package dev.deepdive.transaction.application.seat;

import dev.deepdive.transaction.domain.seat.WorkshopSeat;
import dev.deepdive.transaction.infrastructure.repository.seat.WorkshopSeatRepository;
import org.springframework.stereotype.Service;

@Service
public class SeatService {
    private final WorkshopSeatRepository repository;

    public SeatService(WorkshopSeatRepository repository) { this.repository = repository; }

    public long reserve(long workshopId, int seatCount) {
        WorkshopSeat workshop = repository.findById(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException(workshopId));
        return workshop.reserve(seatCount);
    }
}
