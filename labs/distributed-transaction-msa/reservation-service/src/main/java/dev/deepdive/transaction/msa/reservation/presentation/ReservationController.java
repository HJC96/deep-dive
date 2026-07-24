package dev.deepdive.transaction.msa.reservation.presentation;

import dev.deepdive.transaction.msa.reservation.application.ReservationService;
import dev.deepdive.transaction.msa.reservation.application.dto.result.ReservationResult;
import dev.deepdive.transaction.msa.reservation.presentation.dto.request.CreateReservationRequest;
import dev.deepdive.transaction.msa.reservation.presentation.dto.response.ReservationResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reservations")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ReservationResponse create(@Valid @RequestBody CreateReservationRequest request) {
        return ReservationResponse.from(reservationService.create(request.toCommand()));
    }

    @PostMapping("/{reservationId}/place")
    public ReservationResponse place(@PathVariable("reservationId") long reservationId) {
        ReservationResult result = reservationService.place(reservationId);
        return ReservationResponse.from(result);
    }
}
