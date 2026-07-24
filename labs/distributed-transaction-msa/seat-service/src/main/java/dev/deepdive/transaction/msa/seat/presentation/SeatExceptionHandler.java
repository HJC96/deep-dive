package dev.deepdive.transaction.msa.seat.presentation;

import dev.deepdive.transaction.msa.seat.application.WorkshopNotFoundException;
import dev.deepdive.transaction.msa.seat.domain.NotEnoughSeatsException;
import dev.deepdive.transaction.msa.seat.presentation.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SeatExceptionHandler {
    @ExceptionHandler(WorkshopNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(WorkshopNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("WORKSHOP_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(NotEnoughSeatsException.class)
    public ResponseEntity<ErrorResponse> handleUnavailable(NotEnoughSeatsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("NOT_ENOUGH_SEATS", exception.getMessage()));
    }
}
