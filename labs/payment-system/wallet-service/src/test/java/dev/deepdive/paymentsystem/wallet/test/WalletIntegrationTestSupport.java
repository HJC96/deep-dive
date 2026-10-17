package dev.deepdive.paymentsystem.wallet.test;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * wallet-service 통합 테스트 공통 기반. MySQL·Kafka 를 Testcontainers 싱글톤 컨테이너로 띄운다.
 * 컨테이너는 stop 하지 않는다(캐시된 Spring 컨텍스트가 공유하므로). JVM 종료 시 Ryuk 이 정리한다.
 */
@SpringBootTest
public abstract class WalletIntegrationTestSupport {

    public static final String PAYMENT_TOPIC = "payment";
    public static final String WALLET_TOPIC = "wallet";
    public static final String DLQ_TOPIC = "payment_wallet_dlq";

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("wallet_db")
            .withUsername("test")
            .withPassword("test");

    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));

    static {
        MYSQL.start();
        KAFKA.start();
        createTopics();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
        // DLQ 로 가는 경로를 테스트에서 빨리 확인하려고 재시도 횟수·간격만 줄인다(운영 설정은 max-attempts 5).
        registry.add("spring.cloud.stream.bindings.consume-in-0.consumer.max-attempts", () -> "2");
        registry.add("spring.cloud.stream.bindings.consume-in-0.consumer.back-off-initial-interval", () -> "100");
    }

    public static String kafkaBootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    private static void createTopics() {
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic(PAYMENT_TOPIC, 6, (short) 1),
                    new NewTopic(WALLET_TOPIC, 1, (short) 1),
                    // 바인더는 DLQ 에도 원래 레코드와 같은 파티션 번호로 보낸다. DLQ 파티션 수가 원본(payment, 6)보다 적으면 보내지 못한다.
                    new NewTopic(DLQ_TOPIC, 6, (short) 1)
            )).all().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("테스트 토픽 준비 실패", e);
        }
    }
}
