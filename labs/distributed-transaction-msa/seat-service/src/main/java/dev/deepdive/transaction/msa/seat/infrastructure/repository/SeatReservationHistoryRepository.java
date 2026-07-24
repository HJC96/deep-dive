package dev.deepdive.transaction.msa.seat.infrastructure.repository;

import dev.deepdive.transaction.msa.seat.domain.SeatReservationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatReservationHistoryRepository extends JpaRepository<SeatReservationHistory, Long> {}
