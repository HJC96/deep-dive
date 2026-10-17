package dev.deepdive.paymentsystem.payment.test;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * payment-service 통합 테스트 공통 기반. MySQL·Kafka 를 Testcontainers 싱글톤 컨테이너로 띄운다.
 *
 * <p>컨테이너는 한 번 start 하고 stop 하지 않는다. 여러 테스트 클래스가 캐시된 Spring 컨텍스트를 공유하므로
 * 클래스마다 내리면 죽은 포트를 가리키게 된다. JVM 이 끝나면 Testcontainers(Ryuk)가 정리한다.
 * 모든 테스트 클래스가 같은 애너테이션 조합을 쓰게 해서 컨텍스트도 하나만 뜬다.
 */
@SpringBootTest
@Import(PaymentTestConfiguration.class)
public abstract class PaymentIntegrationTestSupport {

    public static final String PAYMENT_TOPIC = "payment";
    public static final int PAYMENT_TOPIC_PARTITIONS = 6;

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("payment_db")
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
        registry.add("spring.r2dbc.url", () -> "r2dbc:mysql://%s:%d/%s".formatted(
                MYSQL.getHost(), MYSQL.getMappedPort(3306), MYSQL.getDatabaseName()));
        registry.add("spring.r2dbc.username", MYSQL::getUsername);
        registry.add("spring.r2dbc.password", MYSQL::getPassword);
        registry.add("spring.cloud.stream.kafka.binder.brokers", KAFKA::getBootstrapServers);
        // 스케줄러가 백그라운드에서 복구·릴레이를 돌리면 테스트가 기대한 상태를 바꿔 버린다. 테스트는 유스케이스를 직접 부른다.
        registry.add("payment.scheduler.enabled", () -> "false");
    }

    public static String kafkaBootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    // 바인더는 auto-create-topics: false 라 토픽이 없으면 기동하지 못한다. 운영과 같은 토픽을 미리 만든다.
    private static void createTopics() {
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic(PAYMENT_TOPIC, PAYMENT_TOPIC_PARTITIONS, (short) 1),
                    new NewTopic("wallet", 1, (short) 1),
                    new NewTopic("ledger", 1, (short) 1),
                    new NewTopic("wallet_payment_dlq", 1, (short) 1),
                    new NewTopic("ledger_payment_dlq", 1, (short) 1)
            )).all().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("테스트 토픽 준비 실패", e);
        }
    }
}
