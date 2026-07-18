package dev.deepdive.springcacheredis.book.cache;

import dev.deepdive.springcacheredis.book.domain.Book;
import dev.deepdive.springcacheredis.book.service.BookOriginService;
import dev.deepdive.springcacheredis.config.RedisCacheConfig;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Spring Cache 추상화로 Cache Aside를 구현하는 예제다.
 *
 * Redis GET/SET 코드는 Spring의 캐시 프록시와 RedisCacheManager가 대신 처리하므로,
 * 메서드 본문에는 cache miss일 때 실행할 원본 조회만 남는다.
 */
@Service
public class SpringCacheBookService {

    private final BookOriginService bookOriginService;

    public SpringCacheBookService(BookOriginService bookOriginService) {
        this.bookOriginService = bookOriginService;
    }

    /**
     * 첫 호출은 MySQL을 조회한 뒤 결과를 Redis에 저장하고,
     * 같은 id의 다음 호출은 메서드 본문을 실행하지 않고 Redis 값을 반환한다.
     *
     * Optional.empty()의 실제 캐시 값은 null로 취급되므로 unless 조건으로 저장하지 않는다.
     */
    @Cacheable(
            cacheNames = RedisCacheConfig.BOOKS_CACHE,
            key = "#id",
            unless = "#result == null"
    )
    public Optional<Book> findBookById(long id) {
        return bookOriginService.findById(id);
    }
}
