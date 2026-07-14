package dev.deepdive.springcacheredis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.deepdive.springcacheredis.support.RedisAndMySqlContainerTest;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;

class RedisBasicCommandTest extends RedisAndMySqlContainerTest {

    @Test
    void Redis는_key_value_TTL_SETNX_자료구조를_제공한다() {
        redisTemplate.opsForValue().set("redis:hello", "world");

        assertThat(redisTemplate.opsForValue().get("redis:hello")).isEqualTo("world");
        assertThat(redisTemplate.hasKey("redis:hello")).isTrue();

        redisTemplate.opsForValue().set("redis:temporary", "value", Duration.ofSeconds(10));
        Long ttlSeconds = redisTemplate.getExpire("redis:temporary", TimeUnit.SECONDS);

        assertThat(ttlSeconds).isBetween(1L, 10L);

        Boolean firstLock = redisTemplate.opsForValue()
                .setIfAbsent("redis:lock:main-page", "owner-1", Duration.ofSeconds(5));
        Boolean secondLock = redisTemplate.opsForValue()
                .setIfAbsent("redis:lock:main-page", "owner-2", Duration.ofSeconds(5));

        assertThat(firstLock).isTrue();
        assertThat(secondLock).isFalse();
        assertThat(redisTemplate.opsForValue().get("redis:lock:main-page")).isEqualTo("owner-1");

        redisTemplate.opsForZSet().add("book:ranking", "Clean Code", 90);
        redisTemplate.opsForZSet().add("book:ranking", "Effective Java", 100);

        Set<String> ranking = redisTemplate.opsForZSet().reverseRange("book:ranking", 0, -1);

        assertThat(ranking).containsExactly("Effective Java", "Clean Code");
    }

    @Test
    void Redis_INFO는_운영_관찰의_출발점이다() {
        redisTemplate.opsForValue().set("redis:observability", "on");

        Properties info = redisTemplate.execute((RedisCallback<Properties>) connection ->
                connection.serverCommands().info("stats"));

        assertThat(info).isNotNull();
        assertThat(info).containsKey("total_commands_processed");
    }
}
