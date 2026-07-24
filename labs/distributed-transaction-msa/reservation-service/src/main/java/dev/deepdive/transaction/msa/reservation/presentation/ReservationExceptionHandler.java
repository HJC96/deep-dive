package dev.deepdive.transaction.msa.reservation.presentation;

import dev.deepdive.transaction.msa.reservation.application.ReservationNotFoundException;
import dev.deepdive.transaction.msa.reservation.presentation.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ReservationExceptionHandler {
    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ReservationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("RESERVATION_NOT_FOUND", exception.getMessage()));
    }
}
