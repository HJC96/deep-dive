package dev.deepdive.seat.repository;

import dev.deepdive.seat.core.SeatReservation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatReservationRepository extends JpaRepository<SeatReservation, Long> {

    boolean existsBySeatNo(String seatNo);

    boolean existsByUserId(long userId);

    List<SeatReservation> findAllBySeatNo(String seatNo);

    long countBySeatNoStartingWith(String prefix);
}
