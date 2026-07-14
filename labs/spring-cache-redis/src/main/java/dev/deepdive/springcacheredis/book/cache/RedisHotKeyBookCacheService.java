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

    /**
     * 여러 요청이 동시에 같은 책을 조회할 때 MySQL 조회가 한꺼번에 몰리는 것을 막는다.
     *
     * 흐름은 다음과 같다.
     * 1. 캐시가 있으면 바로 반환한다.
     * 2. 캐시가 없으면 한 요청만 Redis 락을 획득해 MySQL을 조회한다.
     * 3. 락을 얻지 못한 요청은 MySQL 대신 Redis 캐시가 채워지기를 기다린다.
     *
     * 이 코드는 분산 락의 동작을 보여주기 위한 학습용 구현이다.
     */
    public Optional<Book> findWithDistributedLock(long id) {
        // 예: id가 1이면 실제 책 데이터는 "hot-book:1"에 저장한다.
        String key = "hot-book:" + id;

        // 캐시가 이미 있다면 락을 사용할 필요가 없다. 바로 역직렬화해서 반환한다.
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return Optional.of(objectMapper.readValue(cached, Book.class));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Book deserialization failed", e);
            }
        }

        // 책 데이터와 락을 구분하기 위해 별도의 락 키를 사용한다.
        // 예: "hot-book:1:lock"
        String lockKey = "hot-book:" + id + ":lock";

        // 각 요청마다 고유한 토큰을 만들어 나중에 "내가 획득한 락"인지 확인한다.
        String token = UUID.randomUUID().toString();

        // setIfAbsent()는 락 키가 없을 때만 값을 저장한다(SET NX).
        // Redis가 이 동작을 원자적으로 처리하므로 동시에 요청해도 한 요청만 true를 받는다.
        // 3초 TTL은 락을 가진 애플리케이션이 비정상 종료돼도 락이 영구히 남지 않게 한다.
        if (Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(lockKey, token, Duration.ofSeconds(3)))) {
            try {
                // 첫 캐시 조회와 락 획득 사이에 다른 요청이 캐시를 채웠을 수 있다.
                // 그래서 락을 획득한 뒤 한 번 더 확인한다(Double-Checked Locking).
                String doubleChecked = redisTemplate.opsForValue().get(key);
                if (doubleChecked != null) {
                    try {
                        return Optional.of(objectMapper.readValue(doubleChecked, Book.class));
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("Book deserialization failed", e);
                    }
                }

                // 두 번째 캐시 조회도 실패한 경우에만 락 소유자가 MySQL을 조회한다.
                Optional<Book> loaded = bookOriginService.findById(id);

                // 책이 실제로 존재할 때만 10분 동안 캐싱한다.
                // Optional.empty()는 저장하지 않으므로 이 메서드는 Negative Caching을 하지 않는다.
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
                // try 안에서 return하거나 예외가 발생해도 finally는 반드시 실행된다.
                // 토큰 비교와 삭제를 Lua로 한 번에 처리해서, 내 락일 때만 삭제한다.
                // 기존 락이 만료된 뒤 다른 요청이 새 락을 얻었다면 토큰이 다르므로 삭제하지 않는다.
                //
                // KEYS[1]과 ARGV[1]은 모두 애플리케이션이 Lua 실행 요청과 함께 전달한다.
                // KEYS[1] = Redis에서 조회하고 삭제할 위치. List.of(lockKey)가 여기에 들어간다.
                // ARGV[1] = 그 위치의 값과 비교할 예상 토큰. 현재 요청의 token이 여기에 들어간다.
                // redis.call('GET', KEYS[1]) = Redis에 현재 저장되어 있는 실제 락 토큰이다.
                //
                // 1. GET으로 Redis에 저장된 락 토큰을 읽는다.
                // 2. 현재 요청의 토큰과 같으면 DEL로 락을 삭제하고 1을 반환한다.
                // 3. 토큰이 다르거나 락이 이미 없으면 삭제하지 않고 0을 반환한다.
                //
                // GET과 DEL을 Java에서 따로 실행하면 그 사이에 락이 바뀔 수 있다.
                // Lua 스크립트는 Redis에서 중간에 끼어드는 명령 없이 실행되므로 비교와 삭제가 원자적이다.
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

        // 여기까지 왔다면 다른 요청이 락을 가지고 있다.
        // DB를 다시 조회하지 않고, 락 소유자가 Redis 캐시를 채울 때까지 기다린다.
        // sleep 시간만 합치면 최대 약 1초다(10ms * 100회).
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

        // 주의: 여기서 empty는 "책이 없음"뿐 아니라 "대기 시간 안에 캐시가 안 채워짐"일 수도 있다.
        return Optional.empty();
    }
}
