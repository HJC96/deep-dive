package dev.deepdive.seat.repository;

import dev.deepdive.seat.core.FailedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailedEventRepository extends JpaRepository<FailedEvent, Long> {
}
