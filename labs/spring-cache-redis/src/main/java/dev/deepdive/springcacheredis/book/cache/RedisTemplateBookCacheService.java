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
        if ("__NULL__".equals(cached)) {
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
            redisTemplate.opsForValue().set(key, "__NULL__", Duration.ofSeconds(30));
        }
        return loaded;
    }

    public void cacheBooksWithJitter(Collection<Book> books, Duration baseTtl, Duration maxJitter) {
        for (Book book : books) {
            String key = "book:" + book.id();
            Duration ttl = baseTtl;
            if (!maxJitter.isZero() && !maxJitter.isNegative()) {
                long jitterMillis = Math.floorMod(key.hashCode(), maxJitter.toMillis() + 1);
                ttl = baseTtl.plusMillis(jitterMillis);
            }
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(book), ttl);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Book serialization failed", e);
            }
        }
    }
}
