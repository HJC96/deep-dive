package dev.deepdive.springcacheredis.book.service;

import dev.deepdive.springcacheredis.book.domain.Book;
import dev.deepdive.springcacheredis.book.repository.BookEntity;
import dev.deepdive.springcacheredis.book.repository.BookRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class BookOriginService {

    private final BookRepository bookRepository;
    private final AtomicInteger readCount = new AtomicInteger(); // 몇번 호출됐는지 확인용.
    private volatile Duration readDelay = Duration.ZERO;

    public BookOriginService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public Optional<Book> findById(long id) {
        readCount.incrementAndGet();
        Duration delay = readDelay;
        if (!delay.isZero() && !delay.isNegative()) {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while simulating origin read delay", e);
            }
        }
        return bookRepository.findById(id)
                .map(BookEntity::toBook);
    }

    public void resetMetrics(Duration readDelay) {
        readCount.set(0);
        this.readDelay = readDelay;
    }

    public int readCount() {
        return readCount.get();
    }
}
