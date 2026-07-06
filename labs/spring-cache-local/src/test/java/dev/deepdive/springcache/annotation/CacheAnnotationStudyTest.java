package dev.deepdive.springcache.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.deepdive.springcache.book.BookEntity;
import dev.deepdive.springcache.book.BookRepository;
import dev.deepdive.springcache.book.StudyBookService;
import dev.deepdive.springcache.support.MySQLContainerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// MySQL Testcontainers와 JPA Repository를 사용하되, 관찰 대상은 Spring Cache 어노테이션의 차이다.
class CacheAnnotationStudyTest extends MySQLContainerTest {

    @Autowired
    private StudyBookService studyBookService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoSpyBean
    private BookRepository bookRepository;

    @BeforeEach
    void setUp() {
        // 테스트 데이터도 Map이 아니라 JPA Repository로 실제 MySQL에 넣는다.
        studyBookService.reset();
        // 테스트마다 캐시 상태를 비워서 이전 테스트의 hit가 섞이지 않게 한다.
        studyBooksCache().clear();
        clearInvocations(bookRepository);
    }

    @Test
    void Cacheable은_cache_miss에서만_메서드를_실행하고_hit에서는_캐시값을_반환한다() {
        String first = studyBookService.findName(1L);
        String second = studyBookService.findName(1L);

        assertThat(first).isEqualTo("Effective Java");
        assertThat(second).isEqualTo(first);
        assertThat(studyBooksCache().get(1L).get()).isEqualTo("Effective Java");
        verify(bookRepository, times(1)).findNameById(1L);
    }

    @Test
    void CachePut은_캐시_hit여부와_상관없이_메서드를_실행하고_결과로_캐시를_덮어쓴다() {
        studyBookService.findName(1L);
        clearInvocations(bookRepository);

        String updated = studyBookService.renameWithCachePut(1L, "Effective Java 4th");
        String cached = studyBookService.findName(1L);

        assertThat(updated).isEqualTo("Effective Java 4th");
        assertThat(cached).isEqualTo("Effective Java 4th");
        assertThat(studyBooksCache().get(1L).get()).isEqualTo("Effective Java 4th");
        verify(bookRepository, times(1)).findById(1L);
        verify(bookRepository, times(1)).save(any(BookEntity.class));
        verify(bookRepository, never()).findNameById(1L);
    }

    @Test
    void CacheEvict는_메서드_실행_후_캐시를_삭제해서_다음_조회가_cache_miss가_되게_한다() {
        studyBookService.findName(1L);
        clearInvocations(bookRepository);

        String updated = studyBookService.renameWithCacheEvict(1L, "Effective Java Revised");

        assertThat(updated).isEqualTo("Effective Java Revised");
        assertThat(studyBooksCache().get(1L)).isNull();

        String reloaded = studyBookService.findName(1L);

        assertThat(reloaded).isEqualTo("Effective Java Revised");
        verify(bookRepository, times(1)).findById(1L);
        verify(bookRepository, times(1)).save(any(BookEntity.class));
        verify(bookRepository, times(1)).findNameById(1L);
    }

    @Test
    void CacheEvict_allEntries는_특정_key가_아니라_캐시_전체를_비운다() {
        studyBookService.findName(1L);
        studyBookService.findName(2L);
        clearInvocations(bookRepository);

        studyBookService.evictAll();

        assertThat(studyBooksCache().get(1L)).isNull();
        assertThat(studyBooksCache().get(2L)).isNull();

        studyBookService.findName(1L);
        studyBookService.findName(2L);

        verify(bookRepository, times(1)).findNameById(1L);
        verify(bookRepository, times(1)).findNameById(2L);
    }

    private Cache studyBooksCache() {
        return cacheManager.getCache("studyBooks");
    }
}
