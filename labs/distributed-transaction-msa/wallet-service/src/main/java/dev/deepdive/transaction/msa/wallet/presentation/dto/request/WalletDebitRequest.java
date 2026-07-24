package dev.deepdive.transaction.msa.wallet.presentation.dto.request;

import jakarta.validation.constraints.Min;

public record WalletDebitRequest(
        @Min(1) long requestId,
        @Min(1) long userId,
        @Min(1) long amount
) {}
