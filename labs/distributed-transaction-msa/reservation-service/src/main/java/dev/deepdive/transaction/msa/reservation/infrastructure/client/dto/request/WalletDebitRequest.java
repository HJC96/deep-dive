package dev.deepdive.transaction.msa.reservation.infrastructure.client.dto.request;

public record WalletDebitRequest(long requestId, long userId, long amount) {}
