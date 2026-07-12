package dev.deepdive.springcacheredis.book.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.deepdive.springcacheredis.book.domain.Book;
import dev.deepdive.springcacheredis.book.service.BookOriginService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisHotKeyBookCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BookOriginService bookOriginService;

    public RedisHotKeyBookCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            BookOriginService bookOriginService
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.bookOriginService = bookOriginService;
    }

    public Optional<Book> findWithoutLock(long id) {
        String key = "hot-book:" + id;
        String cached = redisTemplate.opsForValue().get(key);
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

    public Optional<Book> findWithDistributedLock(long id) {
        String key = "hot-book:" + id;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return Optional.of(objectMapper.readValue(cached, Book.class));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Book deserialization failed", e);
            }
        }

        String lockKey = "hot-book:" + id + ":lock";
        String token = UUID.randomUUID().toString();
        if (Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(lockKey, token, Duration.ofSeconds(3)))) {
            try {
                String doubleChecked = redisTemplate.opsForValue().get(key);
                if (doubleChecked != null) {
                    try {
                        return Optional.of(objectMapper.readValue(doubleChecked, Book.class));
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
            } finally {
                redisTemplate.execute(
                        new DefaultRedisScript<>(
                                """
                                if redis.call('GET', KEYS[1]) == ARGV[1] then
                                    return redis.call('DEL', KEYS[1])
                                end
                                return 0
                                """,
                                Long.class
                        ),
                        List.of(lockKey),
                        token
                );
            }
        }

        for (int i = 0; i < 100; i++) {
            try {
                Thread.sleep(Duration.ofMillis(10).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for hot key cache fill", e);
            }

            String cachedAfterWaiting = redisTemplate.opsForValue().get(key);
            if (cachedAfterWaiting != null) {
                try {
                    return Optional.of(objectMapper.readValue(cachedAfterWaiting, Book.class));
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Book deserialization failed", e);
                }
            }
        }
        return Optional.empty();
    }
}
