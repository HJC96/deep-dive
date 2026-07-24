package dev.deepdive.transaction.msa.reservation.infrastructure.client;

import dev.deepdive.transaction.msa.reservation.infrastructure.client.dto.request.WalletDebitRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WalletClient {
    private final RestClient restClient;

    public WalletClient(RestClient.Builder builder, @Value("${services.wallet.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public void debit(long requestId, long userId, long amount) {
        restClient.post()
                .uri("/internal/wallets/debit")
                .body(new WalletDebitRequest(requestId, userId, amount))
                .retrieve()
                .toBodilessEntity();
    }
}
