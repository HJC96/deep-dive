package dev.deepdive.transaction.msa.seat.presentation;

import dev.deepdive.transaction.msa.seat.application.SeatService;
import dev.deepdive.transaction.msa.seat.presentation.dto.request.SeatReserveRequest;
import dev.deepdive.transaction.msa.seat.presentation.dto.response.SeatReserveResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/seats")
public class SeatController {
    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @PostMapping("/reserve")
    public SeatReserveResponse reserve(@Valid @RequestBody SeatReserveRequest request) {
        long amount = seatService.reserve(request.requestId(), request.workshopId(), request.seatCount());
        return new SeatReserveResponse(request.requestId(), amount);
    }
}
