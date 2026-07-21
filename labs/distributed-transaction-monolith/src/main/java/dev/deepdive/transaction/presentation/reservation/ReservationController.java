package dev.deepdive.transaction.presentation.reservation;

import dev.deepdive.transaction.application.reservation.ReservationResult;
import dev.deepdive.transaction.application.reservation.ReservationService;
import dev.deepdive.transaction.presentation.reservation.dto.CreateReservationRequest;
import dev.deepdive.transaction.presentation.reservation.dto.ReservationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/reservations")
public class ReservationController {
    private final ReservationService service;

    public ReservationController(ReservationService service) { this.service = service; }

    @PostMapping
    public ReservationResponse create(@Valid @RequestBody CreateReservationRequest request) {
        ReservationResult result = service.create(request.toCommand());
        return ReservationResponse.from(result);
    }

    @PostMapping("/{reservationId}/place")
    public ReservationResponse place(@PathVariable("reservationId") @Min(1) long reservationId) {
        ReservationResult result = service.place(reservationId);
        return ReservationResponse.from(result);
    }
}
