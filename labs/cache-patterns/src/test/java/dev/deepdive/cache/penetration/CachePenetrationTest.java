package dev.deepdive.cache.penetration;

import dev.deepdive.cache.aside.CacheAsideProductService;
import dev.deepdive.cache.domain.Product;
import dev.deepdive.cache.infrastructure.InMemoryCache;
import dev.deepdive.cache.repository.InMemoryProductStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 존재하지 않는 키 반복 조회가 매번 저장소를 때리는 Cache Penetration과, negative 캐싱 / Bloom filter 방어.
 */
class CachePenetrationTest {

    private static final long MISSING_ID = 99L;
    private static final int ATTEMPTS = 5;

    @Test
    void 보호_없는_cache_aside는_없는_키를_매번_저장소로_관통시킨다() {
        InMemoryProductStore store = new InMemoryProductStore(new Product(1L, "keyboard", 120_000));
        InMemoryCache<Long, Product> cache = new InMemoryCache<>();
        CacheAsideProductService service = new CacheAsideProductService(store, cache);

        for (int i = 0; i < ATTEMPTS; i++) {
            assertThat(service.findById(MISSING_ID)).isEmpty();
        }

        // 값이 없으면 캐시에 안 들어가므로(present일 때만 put) 매 요청이 저장소로 관통한다.
        assertThat(store.readCount()).isEqualTo(ATTEMPTS);
    }

    @Test
    void negative_캐싱은_반복되는_저장소_조회를_막는다() {
        InMemoryProductStore store = new InMemoryProductStore(new Product(1L, "keyboard", 120_000));
        NegativeCachingCache<Long, Product> cache = new NegativeCachingCache<>();

        for (int i = 0; i < ATTEMPTS; i++) {
            assertThat(cache.get(MISSING_ID, store::findById)).isEmpty();
        }

        // 첫 조회에서 empty도 캐싱하므로 저장소는 단 1번만 조회된다.
        assertThat(store.readCount()).isEqualTo(1);
        assertThat(cache.hasNegativeEntry(MISSING_ID)).isTrue();
    }

    @Test
    void bloom_filter는_확실히_없는_키에_대해_저장소를_건너뛴다() {
        InMemoryProductStore store = new InMemoryProductStore(new Product(1L, "keyboard", 120_000));
        BloomFilter<Long> existingIds = new BloomFilter<>(1024, 3);
        existingIds.add(1L);

        // 필터가 "확실히 없음"이라고 하면 존재 키만 들어 있는 필터 특성상 저장소를 건너뛴다.
        assertThat(existingIds.mightContain(MISSING_ID)).isFalse();

        Optional<Product> result = Optional.empty();
        if (existingIds.mightContain(MISSING_ID)) {
            result = store.findById(MISSING_ID);
        }

        assertThat(result).isEmpty();
        assertThat(store.readCount()).isZero();
        // 등록된 키는 통과시킨다(있을 수도 있음).
        assertThat(existingIds.mightContain(1L)).isTrue();
    }
}
