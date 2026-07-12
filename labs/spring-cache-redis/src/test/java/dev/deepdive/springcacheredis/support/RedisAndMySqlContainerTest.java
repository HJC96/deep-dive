package dev.deepdive.springcacheredis.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis와 MySQL이 필요한 통합 테스트가 공통으로 상속하는 기반 클래스다.
 *
 * <p>테스트를 실행하면 다음 순서로 동작한다.
 * <ol>
 *     <li>Testcontainers가 Redis와 MySQL Docker 컨테이너를 시작한다.</li>
 *     <li>컨테이너의 실제 접속 정보를 Spring 설정에 등록한다.</li>
 *     <li>Spring Boot 애플리케이션 컨텍스트를 실행한다.</li>
 *     <li>각 테스트 전에 Redis 데이터를 비워 테스트 간 영향을 제거한다.</li>
 * </ol>
 *
 * <p>Docker가 실행 중이지 않으면 이 클래스를 상속한 테스트는 자동으로 건너뛴다.
 */
// 실제 애플리케이션과 동일하게 Spring Bean을 모두 생성해서 통합 테스트한다.
@SpringBootTest
// @Container가 붙은 Docker 컨테이너의 시작과 종료를 JUnit이 관리한다.
@Testcontainers(disabledWithoutDocker = true)
// 한 테스트 클래스가 끝나면 이전 컨테이너 주소를 가진 Spring 컨텍스트를 폐기한다.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class RedisAndMySqlContainerTest {

    private static final int REDIS_CONTAINER_PORT = 6379;

    /*
     * @Container가 붙은 static 필드는 테스트 클래스가 실행되는 동안 한 번만 시작된다.
     * 여기서는 Redis 전용 모듈 대신 범용 GenericContainer로 Redis 이미지를 실행한다.
     */
    @Container
    private static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7.2.5"))
                    .withExposedPorts(REDIS_CONTAINER_PORT);

    // MySQLContainer는 JDBC URL, 사용자 이름, 비밀번호를 편리하게 제공한다.
    @Container
    private static final MySQLContainer MYSQL_CONTAINER =
            new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
                    .withDatabaseName("spring_cache_redis")
                    .withUsername("test")
                    .withPassword("test");

    /*
     * Docker는 충돌을 피하려고 컨테이너 포트를 호스트의 임의 포트에 연결한다.
     * 따라서 application.yml의 고정 주소 대신, 실행된 컨테이너의 주소를 Spring에 전달한다.
     */
    @DynamicPropertySource
    static void connectSpringToContainers(DynamicPropertyRegistry registry) {
        registerRedisProperties(registry);
        registerMySqlProperties(registry);
    }

    private static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add(
                "spring.data.redis.port",
                () -> REDIS_CONTAINER.getMappedPort(REDIS_CONTAINER_PORT)
        );
    }

    private static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);

        // 테스트 시작 시 테이블을 만들고, Spring 컨텍스트가 종료되면 제거한다.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.jdbc.time_zone", () -> "UTC");
    }

    // 하위 테스트에서도 Redis 값을 저장하거나 검증할 수 있도록 protected로 제공한다.
    @Autowired
    protected StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedisBeforeEachTest() {
        RedisCallback<Void> deleteAllRedisData = connection -> {
            // 매 테스트를 빈 Redis에서 시작해야 이전 테스트의 키가 결과에 영향을 주지 않는다.
            connection.serverCommands().flushAll();
            return null;
        };

        redisTemplate.execute(deleteAllRedisData);
    }
}
