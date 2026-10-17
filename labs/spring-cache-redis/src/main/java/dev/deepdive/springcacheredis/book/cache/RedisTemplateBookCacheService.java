package dev.deepdive.springcacheredis.book.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deepdive.springcacheredis.book.domain.Book;
import dev.deepdive.springcacheredis.book.service.BookOriginService;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisTemplateBookCacheService {

    // "DB에 없음"을 표시하는 값. Book JSON과 같은 key(book:{id})를 쓰므로
    // 이 key를 읽는 모든 메서드가 sentinel을 먼저 확인해야 한다. (JSON으로 파싱하면 실패)
    static final String NULL_SENTINEL = "__NULL__";
    private static final Duration NULL_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BookOriginService bookOriginService;

    public RedisTemplateBookCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            BookOriginService bookOriginService
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.bookOriginService = bookOriginService;
    }

    public Optional<Book> findBookById(long id) {
        String key = "book:" + id;
        String cached = redisTemplate.opsForValue().get(key);
        if (NULL_SENTINEL.equals(cached)) {
            // findBookByIdWithNullCaching이 남긴 "없음" 표시도 같은 key에 있으므로 hit(없음)으로 취급한다.
            return Optional.empty();
        }
        if (cached != null) {
            try {
                return Optional.of(objectMapper.readValue(cached, Book.class));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Book deserialization failed", e);
            }
        }

        Optional<Book> loaded = bookOriginService.findById(id);
        if (loaded.isPresent()) {
            try {
                redisTemplate.opsForValue().set(
                        key,
                        objectMapper.writeValueAsString(loaded.get()),
                        Duration.ofMinutes(10)
                );
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Book serialization failed", e);
            }
        }
        return loaded;
    }

    public Optional<Book> findBookByIdWithNullCaching(long id) {
        String key = "book:" + id;
        String cached = redisTemplate.opsForValue().get(key);
        if (NULL_SENTINEL.equals(cached)) {
            return Optional.empty();
        }
        if (cached != null) {
            try {
                return Optional.of(objectMapper.readValue(cached, Book.class));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Book deserialization failed", e);
            }
        }

        Optional<Book> loaded = bookOriginService.findById(id);
        if (loaded.isPresent()) {
            try {
                redisTemplate.opsForValue().set(
                        key,
                        objectMapper.writeValueAsString(loaded.get()),
                        Duration.ofMinutes(10)
                );
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Book serialization failed", e);
            }
        } else {
            redisTemplate.opsForValue().set(key, NULL_SENTINEL, NULL_TTL);
        }
        return loaded;
    }

    public void cacheBooksWithJitter(Collection<Book> books, Duration baseTtl, Duration maxJitter) {
        for (Book book : books) {
            String key = "book:" + book.id();
            Duration ttl = baseTtl;
            if (!maxJitter.isZero() && !maxJitter.isNegative()) {
                ttl = baseTtl.plusMillis(jitterMillis(key, maxJitter.toMillis()));
            }
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(book), ttl);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Book serialization failed", e);
            }
        }
    }

    /**
     * key마다 0~maxJitterMillis 사이의 jitter를 결정론적으로(같은 key면 항상 같은 값) 고른다.
     *
     * <p>{@code key.hashCode()}를 그대로 나머지 연산하면 "book:1"~"book:5"처럼 끝 글자만 다른 key의
     * hash가 1씩 차이 나서 jitter도 133~137ms처럼 거의 같아진다(최대 10초를 의도해도 5ms 안에 몰림).
     * SplitMix64 finalizer로 비트를 섞은 뒤 나머지를 취해, 인접한 key도 범위 전체로 흩어지게 한다.
     */
    static long jitterMillis(String key, long maxJitterMillis) {
        if (maxJitterMillis <= 0) {
            return 0L;
        }
        return Math.floorMod(mix(key.hashCode()), maxJitterMillis + 1);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
