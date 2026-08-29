package dev.deepdive.paymentsystem.payment.adapter.out.web.toss.config;

import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Toss Payments API 를 호출하는 {@link WebClient} 를 구성한다.
 * 앱 자체는 서블릿(Tomcat)으로 뜨고, WebClient 는 아웃바운드 HTTP 클라이언트로만 쓴다.
 */
@Configuration
public class TossPaymentWebClientConfiguration {

    private final String baseUrl;
    private final String secretKey;

    public TossPaymentWebClientConfiguration(
            @Value("${PSP.toss.url}") String baseUrl,
            @Value("${PSP.toss.secretKey}") String secretKey
    ) {
        this.baseUrl = baseUrl;
        this.secretKey = secretKey;
    }

    @Bean
    public WebClient tossPaymentWebClient() {
        // Toss 는 시크릿 키를 username 으로 쓰는 HTTP Basic 인증을 요구한다. 비밀번호 자리는 비운다("{secretKey}:").
        String encodedSecretKey = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecretKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(reactorClientHttpConnector())
                .codecs(configurer -> configurer.defaultCodecs())
                .build();
    }

    private ClientHttpConnector reactorClientHttpConnector() {
        ConnectionProvider provider = ConnectionProvider.builder("toss-payment")
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .doOnConnected(connection ->
                        connection.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS)));

        return new ReactorClientHttpConnector(httpClient);
    }
}
