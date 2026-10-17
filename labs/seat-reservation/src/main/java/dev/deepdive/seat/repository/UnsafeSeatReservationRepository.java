package dev.deepdive.seat.repository;

import dev.deepdive.seat.core.UnsafeSeatReservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnsafeSeatReservationRepository extends JpaRepository<UnsafeSeatReservation, Long> {

    boolean existsBySeatNo(String seatNo);
}
