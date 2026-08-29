package dev.deepdive.paymentsystem.payment.adapter.out.web.toss.executor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class TossPaymentExecutor {

    private final WebClient tossPaymentWebClient;
    private final String uri;

    @Autowired
    public TossPaymentExecutor(WebClient tossPaymentWebClient) {
        this(tossPaymentWebClient, "/v1/payments/confirm");
    }

    public TossPaymentExecutor(WebClient tossPaymentWebClient, String uri) {
        this.tossPaymentWebClient = tossPaymentWebClient;
        this.uri = uri;
    }

    public Mono<String> execute(String paymentKey, String orderId, String amount) {
        return tossPaymentWebClient.post()
                .uri(uri)
                .bodyValue("""
                        {
                          "paymentKey": "%s",
                          "orderId": "%s",
                          "amount": %s
                        }
                        """.formatted(paymentKey, orderId, amount))
                .retrieve()
                .bodyToMono(String.class);
    }
}
