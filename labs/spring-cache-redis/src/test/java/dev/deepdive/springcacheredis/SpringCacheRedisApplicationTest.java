package dev.deepdive.springcacheredis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.deepdive.springcacheredis.support.RedisAndMySqlContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class SpringCacheRedisApplicationTest extends RedisAndMySqlContainerTest {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    void Redis_기반_Spring_Cache_환경으로_부팅된다() {
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
        assertThat(redisConnectionFactory).isNotNull();
    }
}
