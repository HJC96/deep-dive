package dev.deepdive.transaction.msa.wallet.presentation;

import dev.deepdive.transaction.msa.wallet.application.WalletNotFoundException;
import dev.deepdive.transaction.msa.wallet.domain.NotEnoughCreditException;
import dev.deepdive.transaction.msa.wallet.presentation.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WalletExceptionHandler {
    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(WalletNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("WALLET_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(NotEnoughCreditException.class)
    public ResponseEntity<ErrorResponse> handleUnavailable(NotEnoughCreditException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("NOT_ENOUGH_CREDIT", exception.getMessage()));
    }
}
