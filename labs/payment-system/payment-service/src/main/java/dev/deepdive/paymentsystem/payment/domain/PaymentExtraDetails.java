package dev.deepdive.paymentsystem.payment.domain;

import java.time.LocalDateTime;

public record PaymentExtraDetails(
        PaymentType type,
        PaymentMethod method,
        LocalDateTime approvedAt,
        String orderName,
        PSPConfirmationStatus pspConfirmationStatus,
        long totalAmount,
        String pspRawData
) {
}
