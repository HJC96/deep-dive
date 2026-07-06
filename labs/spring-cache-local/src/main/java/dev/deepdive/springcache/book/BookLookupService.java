package dev.deepdive.springcache.book;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class BookLookupService {

    private final BookRepository bookRepository;

    public BookLookupService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Cacheable(cacheNames = "bookById", key = "#id")
    public Book findBookById(long id) {
        return findBookDirectly(id);
    }

    @Cacheable(cacheNames = "bookName", key = "#id")
    public String findBookNameById(long id) {
        return findBookNameDirectly(id);
    }

    @Cacheable(cacheNames = "bookNameByTableVersion", key = "{#root.target.currentBookTableUpdatedAt(), #id}")
    public String findBookNameByIdWithTableVersion(long id) {
        return findBookNameDirectly(id);
    }

    public Instant currentBookTableUpdatedAt() {
        return bookRepository.findMaxUpdatedAt()
                .orElse(Instant.EPOCH);
    }

    @Cacheable(cacheNames = "bookSummary", key = "#id")
    public String findBookSummary(long id, String language) {
        return findBookNameDirectly(id) + " summary language=" + language;
    }

    @Cacheable(cacheNames = "bookSearchByRecordCondition")
    public List<Long> findBookIdsByRecordCondition(BookSearchCondition condition) {
        return List.of(1L, 2L);
    }

    @Cacheable(cacheNames = "bookSearchByIdentityCondition")
    public List<Long> findBookIdsByIdentityCondition(BookSearchConditionWithoutEquals condition) {
        return List.of(1L, 2L);
    }

    @CachePut(cacheNames = "bookById", key = "#book.id")
    public Book updateBook(Book book) {
        return bookRepository.save(BookEntity.from(book)).toBook();
    }

    @CacheEvict(cacheNames = "bookById", key = "#id")
    public Book renameBookAndEvictCache(long id, String name) {
        Book before = findBookDirectly(id);
        Book after = new Book(id, name, before.price(), before.updatedAt());
        return bookRepository.save(BookEntity.from(after)).toBook();
    }

    public Book renameBookWithoutEvictCache(long id, String name, Instant updatedAt) {
        Book before = findBookDirectly(id);
        Book after = new Book(id, name, before.price(), updatedAt);
        return bookRepository.save(BookEntity.from(after)).toBook();
    }

    public void reset() {
        bookRepository.deleteAllInBatch();
        bookRepository.saveAll(List.of(
                BookEntity.from(new Book(1L, "Effective Java", 45_000)),
                BookEntity.from(new Book(2L, "Clean Code", 33_000))
        ));
    }

    private Book findBookDirectly(long id) {
        return bookRepository.findById(id)
                .map(BookEntity::toBook)
                .orElseThrow(() -> new NoSuchElementException("Book not found. id=" + id));
    }

    private String findBookNameDirectly(long id) {
        return bookRepository.findNameById(id)
                .orElseThrow(() -> new NoSuchElementException("Book not found. id=" + id));
    }
}
