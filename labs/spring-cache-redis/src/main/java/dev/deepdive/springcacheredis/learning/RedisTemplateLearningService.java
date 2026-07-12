package dev.deepdive.springcacheredis.learning;

import java.time.Duration;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * RedisTemplate은 Redis 명령을 실행하는 창구다.
 *
 * Redis 자료형마다 먼저 해당하는 opsFor...() API를 선택한다.
 *
 * <pre>
 * opsForValue() : String      -> SET, GET
 * opsForHash()  : Hash        -> HSET, HGETALL
 * opsForList()  : List        -> LPUSH, LRANGE
 * opsForSet()   : Set         -> SADD, SMEMBERS
 * opsForZSet()  : Sorted Set  -> ZADD, ZRANGE
 * </pre>
 *
 * 이 예제는 opsForValue()와 opsForHash()를 직접 다룬다.
 */
@Service
public class RedisTemplateLearningService {

    private final StringRedisTemplate redisTemplate;

    public RedisTemplateLearningService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 문자열 자료형은 opsForValue()로 다룬다. 내부적으로 Redis SET 명령이 실행된다.
    public void save(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    // GET 결과가 없으면 null을 반환한다.
    public String find(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 키를 삭제하고, 실제로 삭제된 경우 true를 반환한다.
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    // set의 세 번째 인자로 TTL을 전달하면 저장과 만료 시간 설정을 한 번에 처리한다.
    public void saveWithTtl(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    // 키가 없을 때만 저장한다. Redis의 SET key value NX EX 명령과 같은 동작이다.
    public boolean saveIfAbsent(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
    }

    // Hash는 하나의 Redis 키 아래에 field-value 쌍을 여러 개 저장한다.
    public void putHash(String key, String field, String value) {
        redisTemplate.<String, String>opsForHash().put(key, field, value);
    }

    // Hash의 모든 field-value 쌍을 조회한다. Redis HGETALL에 해당한다.
    public Map<String, String> findHash(String key) {
        return redisTemplate.<String, String>opsForHash().entries(key);
    }
}
