package dev.deepdive.transaction.infrastructure.repository.seat;

import dev.deepdive.transaction.domain.seat.WorkshopSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkshopSeatRepository extends JpaRepository<WorkshopSeat, Long> {}
