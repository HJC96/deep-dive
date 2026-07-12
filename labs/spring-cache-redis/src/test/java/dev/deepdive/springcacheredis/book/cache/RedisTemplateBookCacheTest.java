package dev.deepdive.springcacheredis.book.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.deepdive.springcacheredis.book.domain.Book;
import dev.deepdive.springcacheredis.book.repository.BookEntity;
import dev.deepdive.springcacheredis.book.repository.BookRepository;
import dev.deepdive.springcacheredis.book.service.BookOriginService;
import dev.deepdive.springcacheredis.support.RedisAndMySqlContainerTest;
import java.time.Duration;
import java.util.List;
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
    }
}
