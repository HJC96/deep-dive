package dev.deepdive.coupon.support;

import dev.deepdive.coupon.repository.CouponRepository;
import dev.deepdive.coupon.repository.VersionedCouponRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
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
    // 여러 테스트 클래스가 캐싱된 Spring 컨텍스트를 공유하므로,
    // 클래스 단위로 컨테이너를 내리면 죽은 포트를 가리키게 된다.
    // 컨테이너는 JVM 종료 시 Testcontainers(Ryuk)가 정리한다.
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("coupon")
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
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 40);
    }

    @Autowired
    protected CouponRepository couponRepository;

    @Autowired
    protected VersionedCouponRepository versionedCouponRepository;

    @BeforeEach
    void resetDatabase() {
        couponRepository.deleteAllInBatch();
        versionedCouponRepository.deleteAllInBatch();
    }

    protected double measureIssueTimeMillis(
            int requestCount,
            int threadPoolSize,
            IssueCommand issueCommand
    ) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requestCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        issueCommand.issue();
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    } finally {
                        done.countDown();
                    }
                });
            }

            long startedAt = System.nanoTime();
            start.countDown();
            done.await();

            if (failure.get() != null) {
                throw new AssertionError("동시 요청 처리 중 예외가 발생했습니다.", failure.get());
            }
            return (System.nanoTime() - startedAt) / 1_000_000.0;
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    protected interface IssueCommand {

        void issue() throws Exception;
    }
}
