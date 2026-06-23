package dev.deepdive.seat.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class RedisContainerTest extends MySQLContainerTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2.5"))
            .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    protected StringRedisTemplate redisTemplate;

    protected void flushRedis() {
        redisTemplate.execute((RedisCallback<Object>) (RedisConnection connection) -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }
}
