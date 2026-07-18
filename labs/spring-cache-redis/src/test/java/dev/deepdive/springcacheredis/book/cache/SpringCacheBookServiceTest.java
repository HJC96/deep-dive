package dev.deepdive.springcacheredis.book.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.deepdive.springcacheredis.book.domain.Book;
import dev.deepdive.springcacheredis.book.repository.BookEntity;
import dev.deepdive.springcacheredis.book.repository.BookRepository;
import dev.deepdive.springcacheredis.book.service.BookOriginService;
import dev.deepdive.springcacheredis.config.RedisCacheConfig;
import dev.deepdive.springcacheredis.support.RedisAndMySqlContainerTest;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SpringCacheBookServiceTest extends RedisAndMySqlContainerTest {

    private static final String CACHE_KEY_PREFIX = "deep-dive:spring-cache-redis::";

    @Autowired
    private SpringCacheBookService springCacheBookService;

    @Autowired
    private BookOriginService bookOriginService;

    @Autowired
    private BookRepository bookRepository;

    @Test
    void Cacheable은_첫_호출만_원본을_조회하고_두번째_호출은_Redis_값을_반환한다() {
        bookRepository.deleteAll();
        bookRepository.saveAndFlush(BookEntity.from(new Book(1L, "Effective Java")));
        bookOriginService.resetMetrics(Duration.ZERO);

        String cacheKey = redisCacheKey(1L);
        Book expected = new Book(1L, "Effective Java");

        // 첫 호출: Redis에 값이 없으므로 실제 메서드가 실행되고 MySQL을 조회한다.
        Optional<Book> firstResult = springCacheBookService.findBookById(1L);

        assertThat(firstResult).contains(expected);
        assertThat(bookOriginService.readCount())
                .as("첫 cache miss에서는 원본 저장소를 한 번 조회한다")
                .isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get(cacheKey))
                .as("첫 조회 결과는 Redis에 JSON으로 저장된다")
                .contains("\"id\":1")
                .contains("\"name\":\"Effective Java\"");
        assertThat(redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS))
                .as("application.yml에 설정한 10분 TTL이 적용된다")
                .isBetween(1L, 600L);

        // 두 번째 호출: Spring 캐시 프록시가 Redis 값을 반환하므로 메서드 본문은 실행되지 않는다.
        Optional<Book> secondResult = springCacheBookService.findBookById(1L);

        assertThat(secondResult).contains(expected);
        assertThat(bookOriginService.readCount())
                .as("cache hit에서는 원본 저장소 조회 횟수가 늘어나지 않는다")
                .isEqualTo(1);
    }

    @Test
    void Cacheable은_Optional_empty를_캐싱하지_않는다() {
        bookRepository.deleteAll();
        bookOriginService.resetMetrics(Duration.ZERO);

        assertThat(springCacheBookService.findBookById(999L)).isEmpty();
        assertThat(springCacheBookService.findBookById(999L)).isEmpty();

        assertThat(bookOriginService.readCount())
                .as("없는 책을 캐싱하지 않으므로 호출할 때마다 원본 저장소를 조회한다")
                .isEqualTo(2);
        assertThat(redisTemplate.hasKey(redisCacheKey(999L))).isFalse();
    }

    private String redisCacheKey(long id) {
        return CACHE_KEY_PREFIX
                + RedisCacheConfig.BOOKS_CACHE
                + "::"
                + id;
    }
}
