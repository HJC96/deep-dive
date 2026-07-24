package dev.deepdive.transaction.msa.seat.application;

import dev.deepdive.transaction.msa.seat.domain.SeatReservationHistory;
import dev.deepdive.transaction.msa.seat.domain.WorkshopSeat;
import dev.deepdive.transaction.msa.seat.infrastructure.repository.SeatReservationHistoryRepository;
import dev.deepdive.transaction.msa.seat.infrastructure.repository.WorkshopSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatService {
    private final WorkshopSeatRepository workshopRepository;
    private final SeatReservationHistoryRepository historyRepository;

    public SeatService(
            WorkshopSeatRepository workshopRepository,
            SeatReservationHistoryRepository historyRepository
    ) {
        this.workshopRepository = workshopRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public long reserve(long requestId, long workshopId, int seatCount) {
        WorkshopSeat workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new WorkshopNotFoundException(workshopId));
        long amount = workshop.reserve(seatCount);
        historyRepository.save(new SeatReservationHistory(requestId, workshopId, seatCount, amount));
        return amount;
    }
}
