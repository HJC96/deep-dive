package dev.deepdive.paymentsystem.payment.adapter.in.web.response;

import org.springframework.http.HttpStatus;

public record ApiResponse<T>(
        int status,
        String message,
        T data
) {

    public static <T> ApiResponse<T> with(HttpStatus httpStatus, String message, T data) {
        return new ApiResponse<>(httpStatus.value(), message, data);
    }
}
