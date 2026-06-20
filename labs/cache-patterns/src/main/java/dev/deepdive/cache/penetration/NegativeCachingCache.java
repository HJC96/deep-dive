package dev.deepdive.cache.penetration;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * "값이 없음"(Optional.empty)도 캐싱해, 존재하지 않는 키 반복 조회가 매번 저장소를 때리는
 * Cache Penetration(관통)을 막는 캐시.
 *
 * <p>실무에서는 negative 엔트리에 짧은 TTL을 둬서, 나중에 실제로 생성된 값을 너무 오래
 * 가리지 않게 한다. 이 예제는 TTL 없이 negative 캐싱의 효과만 보여준다.
 */
public final class NegativeCachingCache<K, V> {

    private final Map<K, Optional<V>> values = new HashMap<>();

    public Optional<V> get(K key, Function<K, Optional<V>> loader) {
        Optional<V> cached = values.get(key);
        if (cached != null) {
            // empty 마커도 hit으로 취급한다.
            return cached;
        }

        Optional<V> loaded = loader.apply(key);
        values.put(key, loaded);
        return loaded;
    }

    public boolean hasNegativeEntry(K key) {
        Optional<V> cached = values.get(key);
        return cached != null && cached.isEmpty();
    }
}
