package dev.deepdive.springcache.book;

import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class StudyBookService {

    private final BookRepository bookRepository;

    public StudyBookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    // miss일 때만 JPA Repository를 호출하고, 반환값을 studyBooks 캐시에 저장한다.
    @Cacheable(cacheNames = "studyBooks", key = "#id")
    public String findName(long id) {
        return findNameDirectly(id);
    }

    // 캐시 hit 여부와 상관없이 DB를 갱신하고, 반환값으로 studyBooks 캐시를 덮어쓴다.
    @CachePut(cacheNames = "studyBooks", key = "#id")
    public String renameWithCachePut(long id, String name) {
        return renameDirectly(id, name);
    }

    // DB 갱신 후 지정한 key의 캐시를 삭제해서 다음 조회가 다시 Repository를 타게 한다.
    @CacheEvict(cacheNames = "studyBooks", key = "#id")
    public String renameWithCacheEvict(long id, String name) {
        return renameDirectly(id, name);
    }

    // 개별 key가 아니라 studyBooks 캐시 전체를 삭제한다.
    @CacheEvict(cacheNames = "studyBooks", allEntries = true)
    public void evictAll() {
    }

    public void reset() {
        bookRepository.deleteAllInBatch();
        bookRepository.saveAll(List.of(
                BookEntity.from(new Book(1L, "Effective Java", 45_000)),
                BookEntity.from(new Book(2L, "Clean Code", 33_000))
        ));
    }

    private String renameDirectly(long id, String name) {
        Book before = bookRepository.findById(id)
                .map(BookEntity::toBook)
                .orElseThrow(() -> new NoSuchElementException("Book not found. id=" + id));
        Book renamed = new Book(id, name, before.price(), before.updatedAt());
        return bookRepository.save(BookEntity.from(renamed)).toBook().name();
    }

    private String findNameDirectly(long id) {
        return bookRepository.findNameById(id)
                .orElseThrow(() -> new NoSuchElementException("Book not found. id=" + id));
    }
}
