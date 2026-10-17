package dev.deepdive.cache.penetration;

import dev.deepdive.cache.aside.CacheAsideProductService;
import dev.deepdive.cache.domain.Product;
import dev.deepdive.cache.infrastructure.InMemoryCache;
import dev.deepdive.cache.repository.InMemoryProductStore;
import dev.deepdive.cache.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 존재하지 않는 키 반복 조회가 매번 저장소를 때리는 Cache Penetration과, negative 캐싱 / Bloom filter 방어.
 */
class CachePenetrationTest {

    private static final long MISSING_ID = 99L;
    private static final int ATTEMPTS = 5;
    private static final long NEGATIVE_TTL = 1_000L;

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
        NegativeCachingCache<Long, Product> cache = new NegativeCachingCache<>(new MutableClock(0), NEGATIVE_TTL);

        for (int i = 0; i < ATTEMPTS; i++) {
            assertThat(cache.get(MISSING_ID, store::findById)).isEmpty();
        }

        // 첫 조회에서 empty도 캐싱하므로 저장소는 단 1번만 조회된다.
        assertThat(store.readCount()).isEqualTo(1);
        assertThat(cache.hasNegativeEntry(MISSING_ID)).isTrue();
    }

    @Test
    void negative_엔트리는_TTL이_지나면_만료되어_나중에_생긴_값을_가리지_않는다() {
        InMemoryProductStore store = new InMemoryProductStore(new Product(1L, "keyboard", 120_000));
        MutableClock clock = new MutableClock(0);
        NegativeCachingCache<Long, Product> cache = new NegativeCachingCache<>(clock, NEGATIVE_TTL);

        assertThat(cache.get(MISSING_ID, store::findById)).isEmpty();

        // 없던 상품이 나중에 생성된다.
        Product created = new Product(MISSING_ID, "mouse", 30_000);
        store.save(created);

        // TTL 안: empty 마커가 아직 hit -> 저장소를 다시 보지 않는다(짧은 동안은 새 값을 가림).
        clock.advance(NEGATIVE_TTL - 1);
        assertThat(cache.get(MISSING_ID, store::findById)).isEmpty();
        assertThat(store.readCount()).isEqualTo(1);

        // TTL 경과: negative 엔트리가 만료되어 저장소를 다시 조회 -> 새 값이 보인다.
        clock.advance(1);
        assertThat(cache.hasNegativeEntry(MISSING_ID)).isFalse();
        assertThat(cache.get(MISSING_ID, store::findById)).contains(created);
        assertThat(store.readCount()).isEqualTo(2);
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
