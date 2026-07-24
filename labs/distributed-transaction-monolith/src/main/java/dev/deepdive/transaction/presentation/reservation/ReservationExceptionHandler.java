package dev.deepdive.transaction.presentation.reservation;

import dev.deepdive.transaction.application.reservation.ReservationNotFoundException;
import dev.deepdive.transaction.application.seat.WorkshopNotFoundException;
import dev.deepdive.transaction.application.wallet.WalletNotFoundException;
import dev.deepdive.transaction.domain.seat.NotEnoughSeatsException;
import dev.deepdive.transaction.domain.wallet.NotEnoughCreditException;
import dev.deepdive.transaction.presentation.reservation.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ReservationExceptionHandler {

    @ExceptionHandler({NotEnoughSeatsException.class, NotEnoughCreditException.class})
    public ResponseEntity<ErrorResponse> handleUnavailable(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("RESERVATION_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler({ReservationNotFoundException.class, WorkshopNotFoundException.class, WalletNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("RESOURCE_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", "request validation failed"));
    }
}
