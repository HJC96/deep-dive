package dev.deepdive.springtestinfra.container;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * {@code @Testcontainers}/{@code @Container} 없이 생명주기를 테스트 코드가 직접 쥔다.
 *
 * <p>선언은 필드에, 시작은 {@code @BeforeAll}에, 종료는 {@code @AfterAll}에 있다. Spring 컨텍스트는 띄우지
 * 않으므로 프로퍼티 등록은 여기서 다루지 않는다.
 */
class ManualContainerLifecycleTest {

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("manual_lifecycle")
            .withUsername("test")
            .withPassword("test");

    private static boolean runningBeforeStart;

    @BeforeAll
    static void startContainer() {
        // 어노테이션이 없으니 여기까지 아무도 시작하지 않았다.
        runningBeforeStart = MYSQL.isRunning();
        MYSQL.start();
    }

    @AfterAll
    static void stopContainer() {
        MYSQL.stop();
        assertThat(MYSQL.isRunning()).isFalse();
    }

    @Test
    void 수동_방식은_테스트_코드가_start와_stop_위치를_직접_가진다() {
        assertThat(runningBeforeStart).isFalse();
        assertThat(MYSQL.isRunning()).isTrue();
        assertThat(MYSQL.getJdbcUrl()).contains("manual_lifecycle");
    }
}
