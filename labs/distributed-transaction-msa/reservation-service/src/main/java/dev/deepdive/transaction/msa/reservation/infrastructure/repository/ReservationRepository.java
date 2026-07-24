package dev.deepdive.transaction.msa.reservation.infrastructure.repository;

import dev.deepdive.transaction.msa.reservation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {}
