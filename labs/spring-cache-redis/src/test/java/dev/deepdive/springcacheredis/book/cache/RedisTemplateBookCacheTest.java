package dev.deepdive.springcacheredis.book.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.deepdive.springcacheredis.book.domain.Book;
import dev.deepdive.springcacheredis.book.repository.BookEntity;
import dev.deepdive.springcacheredis.book.repository.BookRepository;
import dev.deepdive.springcacheredis.book.service.BookOriginService;
import dev.deepdive.springcacheredis.support.RedisAndMySqlContainerTest;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RedisTemplateBookCacheTest extends RedisAndMySqlContainerTest {

    @Autowired
    private RedisTemplateBookCacheService bookCacheService;

    @Autowired
    private BookOriginService bookOriginService;

    @Autowired
    private BookRepository bookRepository;

    @Test
    void RedisTemplate으로_Cache_Aside를_직접_구현하면_miss에서만_저장소를_조회한다() {
        bookRepository.deleteAll();
        bookRepository.saveAllAndFlush(List.of(
                BookEntity.from(new Book(1L, "Effective Java")),
                BookEntity.from(new Book(2L, "Clean Code"))
        ));
        bookOriginService.resetMetrics(Duration.ZERO);

        Book expected = new Book(1L, "Effective Java");

        assertThat(bookCacheService.findBookById(1L)).contains(expected);
        assertThat(bookCacheService.findBookById(1L)).contains(expected);

        assertThat(bookOriginService.readCount()).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get("book:1"))
                .contains("\"id\":1")
                .contains("\"name\":\"Effective Java\"");
    }

    @Test
    void Null_Caching은_없는_데이터도_짧게_캐싱해서_반복_DB_조회를_막는다() {
        bookRepository.deleteAll();
        bookRepository.saveAllAndFlush(List.of(
                BookEntity.from(new Book(1L, "Effective Java")),
                BookEntity.from(new Book(2L, "Clean Code"))
        ));
        bookOriginService.resetMetrics(Duration.ZERO);

        for (int i = 0; i < 20; i++) {
            assertThat(bookCacheService.findBookByIdWithNullCaching(999L)).isEmpty();
        }

        assertThat(bookOriginService.readCount()).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get("book:999"))
                .isEqualTo("__NULL__");
        assertThat(redisTemplate.getExpire("book:999", TimeUnit.SECONDS))
                .isBetween(1L, 30L);
    }

    @Test
    void TTL_Jitter는_캐시_만료_시점을_키마다_다르게_흩뜨린다() {
        bookRepository.deleteAll();
        bookRepository.saveAllAndFlush(List.of(
                BookEntity.from(new Book(1L, "Effective Java")),
                BookEntity.from(new Book(2L, "Clean Code"))
        ));
        bookOriginService.resetMetrics(Duration.ZERO);

        List<Book> books = List.of(
                new Book(1L, "Effective Java"),
                new Book(2L, "Clean Code"),
                new Book(3L, "Java Concurrency in Practice"),
                new Book(4L, "Designing Data-Intensive Applications"),
                new Book(5L, "Refactoring")
        );

        bookCacheService.cacheBooksWithJitter(books, Duration.ofSeconds(60), Duration.ofSeconds(10));

        List<Long> ttlMillis = books.stream()
                .map(Book::id)
                .map(id -> "book:" + id)
                .map(key -> redisTemplate.getExpire(key, TimeUnit.MILLISECONDS))
                .toList();

        assertThat(ttlMillis).allSatisfy(ttl -> assertThat(ttl).isBetween(50_000L, 70_000L));
        assertThat(ttlMillis.stream().distinct().count()).isGreaterThan(1);
        // 끝 글자만 다른 key(book:1~5)라도 jitter가 최대 10초 범위에 넓게 퍼져야 한다.
        // (예전 구현은 key.hashCode()를 그대로 써서 133~137ms 안에 몰렸다)
        long spread = Collections.max(ttlMillis) - Collections.min(ttlMillis);
        assertThat(spread).isGreaterThan(5_000L);
    }

    @Test
    void TTL_Jitter는_인접한_key_100개를_jitter_범위_전체에_고르게_흩뜨린다() {
        long maxJitterMillis = 10_000L;
        Set<Long> secondBuckets = new HashSet<>();
        for (int id = 1; id <= 100; id++) {
            long jitter = RedisTemplateBookCacheService.jitterMillis("book:" + id, maxJitterMillis);
            assertThat(jitter).isBetween(0L, maxJitterMillis);
            secondBuckets.add(jitter / 1_000L);
        }

        // 0~10초를 1초 단위 구간으로 나눴을 때 거의 모든 구간에 key가 들어간다.
        assertThat(secondBuckets).hasSizeGreaterThanOrEqualTo(9);
        // 같은 key는 항상 같은 jitter(결정론적)
        assertThat(RedisTemplateBookCacheService.jitterMillis("book:1", maxJitterMillis))
                .isEqualTo(RedisTemplateBookCacheService.jitterMillis("book:1", maxJitterMillis));
    }

    @Test
    void Null_sentinel이_남은_key를_일반_조회해도_JSON_파싱_실패_없이_빈_결과를_돌려준다() {
        bookRepository.deleteAll();
        bookOriginService.resetMetrics(Duration.ZERO);

        // Null Caching 경로가 같은 key(book:998)에 "__NULL__"을 남긴다.
        assertThat(bookCacheService.findBookByIdWithNullCaching(998L)).isEmpty();
        assertThat(redisTemplate.opsForValue().get("book:998")).isEqualTo("__NULL__");

        // 일반 Cache Aside 조회도 sentinel을 "없음 hit"으로 해석한다 (예전 구현은 JSON 파싱 예외)
        assertThat(bookCacheService.findBookById(998L)).isEmpty();
        assertThat(bookOriginService.readCount()).isEqualTo(1);
    }
}
