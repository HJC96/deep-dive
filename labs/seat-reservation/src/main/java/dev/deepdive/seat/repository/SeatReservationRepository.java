package dev.deepdive.seat.repository;

import dev.deepdive.seat.core.SeatReservation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatReservationRepository extends JpaRepository<SeatReservation, Long> {

    boolean existsBySeatNo(String seatNo);

    boolean existsByUserId(long userId);

    Optional<SeatReservation> findBySeatNo(String seatNo);

    List<SeatReservation> findAllBySeatNo(String seatNo);

    long countBySeatNoStartingWith(String prefix);
}
