package dev.deepdive.springcache.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.deepdive.springcache.support.MySQLContainerTest;
import java.time.Instant;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class BookLookupServiceCacheTest extends MySQLContainerTest {

    @Autowired
    private BookLookupService bookLookupService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoSpyBean
    private BookRepository bookRepository;

    @BeforeEach
    void setUp() {
        bookLookupService.reset();
        cacheManager.getCacheNames()
                .forEach(cacheName -> cacheManager.getCache(cacheName).clear());
        clearInvocations(bookRepository);
    }

    @Test
    void ConcurrentMapCacheManager를_명시적인_로컬_캐시_관리자로_사용한다() {
        Object nativeCache = cacheManager.getCache("bookById").getNativeCache();

        assertThat(cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
        assertThat(nativeCache).isInstanceOf(ConcurrentMap.class);
    }

    @Test
    void Cacheable은_첫_조회에서_miss로_메서드를_실행하고_두_번째_동일_조회에서는_hit로_결과를_반환한다() {
        Book first = bookLookupService.findBookById(1L);
        Book second = bookLookupService.findBookById(1L);

        assertThat(first).isEqualTo(new Book(1L, "Effective Java", 45_000));
        assertThat(second).isEqualTo(first);
        verify(bookRepository, times(1)).findById(1L);
    }

    @Test
    void 캐시_이름이_다르면_같은_ID라도_서로_다른_캐시_공간을_사용한다() {
        Book book = bookLookupService.findBookById(1L);
        String bookName = bookLookupService.findBookNameById(1L);

        bookLookupService.findBookById(1L);
        bookLookupService.findBookNameById(1L);

        verify(bookRepository, times(1)).findById(1L);
        verify(bookRepository, times(1)).findNameById(1L);
        assertThat(cacheManager.getCache("bookById").get(1L).get()).isEqualTo(book);
        assertThat(cacheManager.getCache("bookName").get(1L).get()).isEqualTo(bookName);
    }

    @Test
    void 테이블의_max_updatedAt을_캐시_키에_포함하면_evict없이_테이블_전체가_새_버전으로_miss난다() {
        Instant nextTableVersion = Book.DEFAULT_UPDATED_AT.plusSeconds(60);

        String first = bookLookupService.findBookNameByIdWithTableVersion(1L);
        String cachedBeforeUpdate = bookLookupService.findBookNameByIdWithTableVersion(1L);

        Book unrelatedBook = bookLookupService.renameBookWithoutEvictCache(2L, "Clean Code Revised", nextTableVersion);
        String cacheMissByTableVersion = bookLookupService.findBookNameByIdWithTableVersion(1L);
        String cachedAfterVersionChange = bookLookupService.findBookNameByIdWithTableVersion(1L);

        assertThat(first).isEqualTo("Effective Java");
        assertThat(cachedBeforeUpdate).isEqualTo(first);
        assertThat(unrelatedBook.updatedAt()).isEqualTo(nextTableVersion);
        assertThat(cacheMissByTableVersion).isEqualTo("Effective Java");
        assertThat(cachedAfterVersionChange).isEqualTo(cacheMissByTableVersion);
        assertThat(nativeCache("bookNameByTableVersion")).hasSize(2);
        verify(bookRepository, times(4)).findMaxUpdatedAt();
        verify(bookRepository, times(2)).findNameById(1L);
    }

    @Test
    void CachePut은_캐시가_있어도_메서드를_항상_실행하고_결과로_캐시를_덮어쓴다() {
        Book before = bookLookupService.findBookById(1L);
        Book after = bookLookupService.updateBook(new Book(1L, "Effective Java 4th", 48_000));
        Book cached = bookLookupService.findBookById(1L);
        Book afterSecondUpdate = bookLookupService.updateBook(new Book(1L, "Effective Java 5th", 50_000));

        assertThat(before.name()).isEqualTo("Effective Java");
        assertThat(after.name()).isEqualTo("Effective Java 4th");
        assertThat(cached).isEqualTo(after);
        assertThat(bookLookupService.findBookById(1L)).isEqualTo(afterSecondUpdate);
        verify(bookRepository, times(1)).findById(1L);
        verify(bookRepository, times(2)).save(any(BookEntity.class));
    }

    @Test
    void CacheEvict는_메서드_실행_후_지정한_캐시를_삭제한다() {
        Book before = bookLookupService.findBookById(1L);

        Book renamed = bookLookupService.renameBookAndEvictCache(1L, "Effective Java Revised");
        Book reloaded = bookLookupService.findBookById(1L);

        assertThat(before.name()).isEqualTo("Effective Java");
        assertThat(renamed.name()).isEqualTo("Effective Java Revised");
        assertThat(reloaded).isEqualTo(renamed);
        verify(bookRepository, times(3)).findById(1L);
        verify(bookRepository, times(1)).save(any(BookEntity.class));
    }

    @Test
    void key를_id만_사용하도록_지정하면_다른_파라미터가_달라도_같은_캐시_값을_사용한다() {
        String korean = bookLookupService.findBookSummary(1L, "ko");
        String english = bookLookupService.findBookSummary(1L, "en");

        assertThat(korean).isEqualTo("Effective Java summary language=ko");
        assertThat(english).isEqualTo(korean);
        verify(bookRepository, times(1)).findNameById(1L);
    }

    @Test
    void SimpleKeyGenerator는_파라미터_객체의_equals_hashCode가_같으면_같은_키로_판단한다() {
        BookSearchCondition first = new BookSearchCondition("java", "ko");
        BookSearchCondition second = new BookSearchCondition("java", "ko");

        assertThat(bookLookupService.findBookIdsByRecordCondition(first)).containsExactly(1L, 2L);
        assertThat(bookLookupService.findBookIdsByRecordCondition(second)).containsExactly(1L, 2L);

        assertThat(nativeCache("bookSearchByRecordCondition")).hasSize(1);
    }

    @Test
    void DTO가_equals_hashCode를_제대로_구현하지_않으면_논리적으로_같은_조건도_다른_캐시_키가_된다() {
        BookSearchConditionWithoutEquals first = new BookSearchConditionWithoutEquals("java", "ko");
        BookSearchConditionWithoutEquals second = new BookSearchConditionWithoutEquals("java", "ko");

        assertThat(bookLookupService.findBookIdsByIdentityCondition(first)).containsExactly(1L, 2L);
        assertThat(bookLookupService.findBookIdsByIdentityCondition(second)).containsExactly(1L, 2L);

        assertThat(nativeCache("bookSearchByIdentityCondition")).hasSize(2);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<Object, Object> nativeCache(String cacheName) {
        return (ConcurrentMap<Object, Object>) cacheManager.getCache(cacheName).getNativeCache();
    }
}
