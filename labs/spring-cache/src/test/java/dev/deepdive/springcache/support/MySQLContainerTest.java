package dev.deepdive.springcache.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
public abstract class MySQLContainerTest {

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("spring_cache")
            .withUsername("test")
            .withPassword("test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?serverTimezone=UTC");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.jdbc.time_zone", () -> "UTC");
    }
}
