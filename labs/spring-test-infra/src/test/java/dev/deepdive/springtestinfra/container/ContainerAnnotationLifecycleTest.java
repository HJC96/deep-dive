package dev.deepdive.springtestinfra.container;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ContainerAnnotationLifecycleTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.0.36");

    @Container
    private static final MySQLContainer MANAGED_MYSQL = new MySQLContainer(MYSQL_IMAGE)
            .withDatabaseName("managed_lifecycle")
            .withUsername("test")
            .withPassword("test");

    @Test
    void Container_어노테이션이_없으면_객체를_만들어도_자동으로_시작되지_않는다() {
        MySQLContainer notManagedMySQL = new MySQLContainer(MYSQL_IMAGE)
                .withDatabaseName("not_started")
                .withUsername("test")
                .withPassword("test");

        assertThat(notManagedMySQL.isRunning()).isFalse();
    }

    @Test
    void Container_어노테이션이_있으면_JUnit이_테스트_전에_컨테이너를_시작한다() {
        assertThat(MANAGED_MYSQL.isRunning()).isTrue();
        assertThat(MANAGED_MYSQL.getJdbcUrl()).contains("managed_lifecycle");
    }
}
