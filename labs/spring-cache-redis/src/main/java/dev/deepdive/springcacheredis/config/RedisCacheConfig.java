package dev.deepdive.springcacheredis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Spring Cache가 Redis에 Book을 저장할 때 사용할 설정이다.
 *
 * RedisTemplate 예제에서는 서비스가 직접 JSON 직렬화를 하지만,
 * Spring Cache 예제에서는 RedisCacheManager가 이 설정을 보고 직렬화한다.
 */
@Configuration
public class RedisCacheConfig {

    public static final String BOOKS_CACHE = "books";

    @Bean
    RedisCacheManagerBuilderCustomizer redisCacheJsonCustomizer(ObjectMapper objectMapper) {
        // JacksonConfig가 만든 ObjectMapper Bean을 주입받는다.

        // ObjectMapper만 등록해서는 RedisCacheManager가 자동으로 JSON을 사용하지 않는다.
        // RedisCacheManager가 요구하는 RedisSerializer 형태로 ObjectMapper를 연결해야 한다.

        // 이 객체는 캐스팅을 하는 것이 아니라 다음 두 변환을 담당한다.
        // 저장: Book 같은 Java 객체 -> JSON byte[]
        // 조회: JSON byte[] -> 원래 Java 객체
        GenericJackson2JsonRedisSerializer jsonSerializer =
                GenericJackson2JsonRedisSerializer.builder()
                        // 공용 ObjectMapper를 직접 변경하지 않도록 Redis 캐시 전용 복사본을 사용한다.
                        .objectMapper(objectMapper.copy())
                        // RedisCacheManager는 캐시 값을 Object로 다루므로 JSON에 실제 클래스 정보도 기록한다.
                        // 그래야 조회할 때 LinkedHashMap이 아니라 원래 Book 타입으로 복원할 수 있다.
                        .defaultTyping(true)
                        .build();

        // Spring Cache 설정은 serializer를 직접 받지 않고 읽기와 쓰기 방식을 묶은
        // SerializationPair를 받는다. fromSerializer()는 위 serializer 하나를
        // JSON Writer와 JSON Reader 양쪽에 사용하도록 감싼다.
        SerializationPair<Object> jsonSerialization =
                SerializationPair.fromSerializer(jsonSerializer);

        // 안쪽 builder.cacheDefaults(): Boot가 만든 현재 캐시 설정을 가져온다.
        // serializeValuesWith(): TTL, null 정책, key prefix는 유지하고 value 직렬화만 JSON으로 바꾼다.
        // 바깥쪽 builder.cacheDefaults(...): 변경한 설정을 RedisCacheManager에 다시 적용한다.
        return builder -> builder.cacheDefaults(
                builder.cacheDefaults().serializeValuesWith(jsonSerialization)
        );
    }
}
