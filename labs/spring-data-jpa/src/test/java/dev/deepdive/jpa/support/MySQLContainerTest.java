package dev.deepdive.jpa.support;

import dev.deepdive.jpa.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
public abstract class MySQLContainerTest {

    // 싱글톤 컨테이너 패턴: 한 번 start 후 stop하지 않는다.
    // 여러 테스트 클래스가 캐싱된 Spring 컨텍스트를 공유하므로, 클래스 단위로 컨테이너를 내리면
    // 죽은 포트를 가리키게 된다. 컨테이너는 JVM 종료 시 Testcontainers(Ryuk)가 정리한다.
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("jpa_lab")
            .withUsername("test")
            .withPassword("test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired
    protected ProductRepository productRepository;

    @BeforeEach
    void resetDatabase() {
        productRepository.deleteAllInBatch();
    }
}
