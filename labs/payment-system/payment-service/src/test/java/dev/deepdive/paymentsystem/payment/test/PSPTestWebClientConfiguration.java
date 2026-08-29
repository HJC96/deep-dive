package dev.deepdive.paymentsystem.payment.test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@TestConfiguration
public class PSPTestWebClientConfiguration {

    private final String baseUrl;
    private final String secretKey;

    public PSPTestWebClientConfiguration(
            @Value("${PSP.toss.url}") String baseUrl,
            @Value("${PSP.toss.secretKey}") String secretKey
    ) {
        this.baseUrl = baseUrl;
        this.secretKey = secretKey;
    }

    public WebClient createTestTossWebClient(Map<String, String> customHeaders) {
        String encodedSecretKey = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecretKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultHeaders(httpHeaders -> customHeaders.forEach(httpHeaders::set))
                .clientConnector(reactorClientHttpConnector())
                .build();
    }

    private ClientHttpConnector reactorClientHttpConnector() {
        return new ReactorClientHttpConnector(
                HttpClient.create(ConnectionProvider.builder("test-toss-payment").build()));
    }
}
