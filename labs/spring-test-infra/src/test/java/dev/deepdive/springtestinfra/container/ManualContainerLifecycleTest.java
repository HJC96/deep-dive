package dev.deepdive.springtestinfra.container;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

class ManualContainerLifecycleTest {

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("manual_lifecycle")
            .withUsername("test")
            .withPassword("test");

    private static boolean startCalled;

    @BeforeAll
    static void startContainer() {
        startCalled = true;
        MYSQL.start();
    }

    @AfterAll
    static void stopContainer() {
        MYSQL.stop();
    }

    @Test
    void 수동_방식은_테스트_코드가_start와_stop_위치를_직접_가진다() {
        assertThat(startCalled).isTrue();
        assertThat(MYSQL.isRunning()).isTrue();
        assertThat(MYSQL.getJdbcUrl()).contains("manual_lifecycle");
    }
}
