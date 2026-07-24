package dev.deepdive.transaction.msa.reservation.infrastructure.client;

import dev.deepdive.transaction.msa.reservation.infrastructure.client.dto.request.SeatReserveRequest;
import dev.deepdive.transaction.msa.reservation.infrastructure.client.dto.response.SeatReserveResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SeatClient {
    private final RestClient restClient;

    public SeatClient(RestClient.Builder builder, @Value("${services.seat.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public long reserve(long requestId, long workshopId, int seatCount) {
        SeatReserveResponse response = restClient.post()
                .uri("/internal/seats/reserve")
                .body(new SeatReserveRequest(requestId, workshopId, seatCount))
                .retrieve()
                .body(SeatReserveResponse.class);
        if (response == null) throw new IllegalStateException("seat service returned an empty response");
        return response.amount();
    }
}
