package dev.deepdive.transaction.msa.seat.infrastructure.repository;

import dev.deepdive.transaction.msa.seat.domain.WorkshopSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkshopSeatRepository extends JpaRepository<WorkshopSeat, Long> {}
