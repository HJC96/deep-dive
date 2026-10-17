package dev.deepdive.springtestinfra.container;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 같은 {@code @Testcontainers} 클래스 안에 컨테이너 필드 두 개를 나란히 두고, {@code @Container} 하나로
 * 시작 여부가 갈리는지 본다.
 *
 * <p>{@code @Testcontainers} 확장은 클래스의 필드를 훑어 {@code @Container}가 붙은 것만 시작·종료한다.
 * 같은 클래스, 같은 선언 방식이라도 어노테이션이 없는 필드는 테스트가 도는 동안 계속 꺼져 있다.
 */
@Testcontainers
class ContainerAnnotationLifecycleTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.0.36");

    @Container
    private static final MySQLContainer MANAGED_MYSQL = new MySQLContainer(MYSQL_IMAGE)
            .withDatabaseName("managed_lifecycle")
            .withUsername("test")
            .withPassword("test");

    // @Container가 없다. 확장이 이 필드를 건너뛰므로 아무도 start()를 부르지 않는다.
    private static final MySQLContainer NOT_MANAGED_MYSQL = new MySQLContainer(MYSQL_IMAGE)
            .withDatabaseName("not_managed_lifecycle")
            .withUsername("test")
            .withPassword("test");

    @Test
    void Container_어노테이션이_없는_필드는_같은_클래스에_있어도_시작되지_않는다() {
        // 확장이 컨테이너를 시작하는 단계는 이미 지났다. 관리 대상은 떠 있는데 이쪽은 꺼져 있다.
        assertThat(MANAGED_MYSQL.isRunning()).isTrue();
        assertThat(NOT_MANAGED_MYSQL.isRunning()).isFalse();
    }

    @Test
    void Container_어노테이션이_있으면_JUnit이_테스트_전에_컨테이너를_시작한다() {
        assertThat(MANAGED_MYSQL.isRunning()).isTrue();
        assertThat(MANAGED_MYSQL.getJdbcUrl()).contains("managed_lifecycle");
    }
}
