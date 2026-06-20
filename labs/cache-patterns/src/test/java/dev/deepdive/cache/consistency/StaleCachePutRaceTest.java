package dev.deepdive.cache.consistency;

import dev.deepdive.cache.domain.Product;
import dev.deepdive.cache.infrastructure.InMemoryCache;
import dev.deepdive.cache.repository.InMemoryProductStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cache Aside의 read-after-write race를 결정론적으로 재현하고, 버전으로 막는 방법을 보여준다.
 *
 * <p>race 시나리오:
 * <pre>
 * 읽기 A: miss -> 저장소에서 v1 읽음 ----------------+
 * 쓰기 B: 저장소를 v2로 갱신 -> 캐시 evict           |
 * 읽기 A: (뒤늦게) 캐시에 v1을 put  <- stale!  &lt;---+
 * </pre>
 */
class StaleCachePutRaceTest {

    @Test
    void 보호_없는_cache_aside는_읽기쓰기_교차_시_stale_값을_되살릴_수_있다() {
        InMemoryProductStore store = new InMemoryProductStore(new Product(1L, "keyboard", 120_000));
        InMemoryCache<Long, Product> cache = new InMemoryCache<>();

        // 읽기 A: 캐시 miss 후 저장소에서 v1을 읽었지만 아직 put 전이다.
        Optional<Product> readByA = store.findById(1L);
        Product v1 = readByA.orElseThrow();

        // 쓰기 B: 저장소를 v2로 갱신하고 캐시를 무효화한다.
        Product v2 = new Product(1L, "keyboard", 99_000);
        store.save(v2);
        cache.evict(1L);

        // 읽기 A: 뒤늦게 자신이 읽었던 v1을 캐시에 넣는다 -> stale 값이 다시 박힌다.
        cache.put(1L, v1);

        assertThat(cache.get(1L)).contains(v1);
        assertThat(store.findById(1L)).contains(v2);
        // 캐시(stale v1)와 저장소(v2)가 어긋난다.
        assertThat(cache.get(1L)).isNotEqualTo(store.findById(1L));
    }

    @Test
    void 버전_캐시는_뒤늦은_stale_put을_거부한다() {
        VersionedCache<Long, Product> cache = new VersionedCache<>();

        Product v1 = new Product(1L, "keyboard", 120_000);
        Product v2 = new Product(1L, "keyboard", 99_000);

        // 읽기 A가 버전 1의 v1을 읽음(아직 put 전)
        // 쓰기 B가 버전 2의 v2를 반영
        cache.put(1L, 2L, v2);

        // 읽기 A가 뒤늦게 버전 1의 v1을 put -> 더 낮은 버전이라 거부됨
        cache.put(1L, 1L, v1);

        assertThat(cache.get(1L)).contains(v2);
        assertThat(cache.rejectedPuts()).isEqualTo(1);
    }
}
