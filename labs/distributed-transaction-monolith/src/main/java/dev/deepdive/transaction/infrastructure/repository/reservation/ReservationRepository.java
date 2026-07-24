package dev.deepdive.transaction.infrastructure.repository.reservation;

import dev.deepdive.transaction.domain.reservation.Reservation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from Reservation reservation where reservation.id = :reservationId")
    Optional<Reservation> findByIdForUpdate(@Param("reservationId") long reservationId);
}
