package dev.deepdive.springcacheredis.book.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.deepdive.springcacheredis.book.domain.Book;
import dev.deepdive.springcacheredis.book.repository.BookEntity;
import dev.deepdive.springcacheredis.book.repository.BookRepository;
import dev.deepdive.springcacheredis.book.service.BookOriginService;
import dev.deepdive.springcacheredis.support.RedisAndMySqlContainerTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RedisHotKeyLockTest extends RedisAndMySqlContainerTest {

    @Autowired
    private RedisHotKeyBookCacheService hotKeyBookCacheService;

    @Autowired
    private BookOriginService bookOriginService;

    @Autowired
    private BookRepository bookRepository;

    @Test
    void 핫키가_동시에_miss되면_락이_없을_때_저장소_조회가_몰린다() throws Exception {
        bookRepository.deleteAll();
        bookRepository.saveAndFlush(BookEntity.from(new Book(1L, "Effective Java")));
        bookOriginService.resetMetrics(Duration.ofMillis(80));

        ExecutorService executor = Executors.newFixedThreadPool(40);
        CountDownLatch ready = new CountDownLatch(40);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<Book>>> futures = new ArrayList<>();

        for (int i = 0; i < 40; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return hotKeyBookCacheService.findWithoutLock(1L);
            }));
        }

        ready.await();
        start.countDown();

        List<Optional<Book>> results = new ArrayList<>();
        for (Future<Optional<Book>> future : futures) {
            results.add(future.get());
        }
        executor.shutdownNow();

        // 공통 결과: 락이 없어도 40개 요청 모두 같은 책을 반환해야 한다.
        assertThat(results)
                .as("락이 없어도 모든 요청은 같은 책을 반환한다")
                .allSatisfy(result ->
                        assertThat(result).contains(new Book(1L, "Effective Java")));

        // 비교할 차이: 락이 없으면 동시에 cache miss한 여러 요청이 원본 저장소까지 조회한다.
        assertThat(bookOriginService.readCount())
                .as("락이 없으면 원본 저장소 조회가 여러 번 발생한다")
                .isGreaterThan(1);
    }

    @Test
    void Redis_분산락과_Double_Checked_Locking은_핫키_miss에서_저장소_조회를_줄인다() throws Exception {
        bookRepository.deleteAll();
        bookRepository.saveAndFlush(BookEntity.from(new Book(1L, "Effective Java")));
        bookOriginService.resetMetrics(Duration.ofMillis(80));

        ExecutorService executor = Executors.newFixedThreadPool(40);
        CountDownLatch ready = new CountDownLatch(40);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<Book>>> futures = new ArrayList<>();

        for (int i = 0; i < 40; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return hotKeyBookCacheService.findWithDistributedLock(1L);
            }));
        }

        ready.await();
        start.countDown();

        List<Optional<Book>> results = new ArrayList<>();
        for (Future<Optional<Book>> future : futures) {
            results.add(future.get());
        }
        executor.shutdownNow();

        // 공통 결과: 분산 락을 사용해도 40개 요청 모두 같은 책을 반환해야 한다.
        assertThat(results)
                .as("분산 락을 사용해도 모든 요청은 같은 책을 반환한다")
                .allSatisfy(result ->
                        assertThat(result).contains(new Book(1L, "Effective Java")));

        // 비교할 차이: 분산 락을 사용하면 락 소유자 한 명만 원본 저장소를 조회한다.
        assertThat(bookOriginService.readCount())
                .as("분산 락을 사용하면 원본 저장소 조회는 정확히 한 번이다")
                .isEqualTo(1);
    }
}
