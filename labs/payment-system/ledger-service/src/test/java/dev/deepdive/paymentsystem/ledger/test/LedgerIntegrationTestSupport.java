package dev.deepdive.paymentsystem.ledger.test;

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
 * ledger-service 통합 테스트 공통 기반. MySQL·Kafka 를 Testcontainers 싱글톤 컨테이너로 띄운다.
 *
 * <p>장부 테이블에는 UPDATE/DELETE 금지 트리거가 걸려 있어 {@code deleteAll()} 로 정리할 수 없다.
 * 테스트마다 orderId 를 새로 만들고 그 orderId 로만 조회해 서로 간섭하지 않게 한다.
 */
@SpringBootTest
public abstract class LedgerIntegrationTestSupport {

    public static final String PAYMENT_TOPIC = "payment";
    public static final String LEDGER_TOPIC = "ledger";
    public static final String DLQ_TOPIC = "payment_ledger_dlq";

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("ledger_db")
            .withUsername("test")
            .withPassword("test")
            // 바이너리 로그가 켜진 MySQL 8 에서 SUPER 권한 없는 사용자가 트리거를 만들려면 이 설정이 필요하다.
            .withCommand("--log-bin-trust-function-creators=1");

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
                    new NewTopic(LEDGER_TOPIC, 1, (short) 1),
                    // 바인더는 DLQ 에도 원래 레코드와 같은 파티션 번호로 보낸다. 원본(payment, 6)과 파티션 수를 맞춘다.
                    new NewTopic(DLQ_TOPIC, 6, (short) 1)
            )).all().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("테스트 토픽 준비 실패", e);
        }
    }
}
