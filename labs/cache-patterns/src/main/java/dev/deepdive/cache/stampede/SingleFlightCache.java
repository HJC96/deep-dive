package dev.deepdive.cache.stampede;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * per-key 락으로 같은 키에 대한 동시 로드를 1회로 합치는(single-flight) 캐시.
 *
 * <p>Cache Stampede(thundering herd): 인기 키가 동시에 miss되면 수많은 요청이 한꺼번에
 * 저장소로 몰린다. 키별 락 + 락 안에서의 double-check로, 동시에 들어온 N개의 miss 중
 * 실제 로드는 1번만 일어나고 나머지는 그 결과를 공유한다.
 */
public final class SingleFlightCache<K, V> {

    private final ConcurrentHashMap<K, V> values = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, Object> locks = new ConcurrentHashMap<>();

    public V get(K key, Function<K, V> loader) {
        V cached = values.get(key);
        if (cached != null) {
            return cached;
        }

        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            // 락을 잡은 사이 다른 스레드가 이미 채웠을 수 있으므로 다시 확인한다.
            V again = values.get(key);
            if (again != null) {
                return again;
            }

            V loaded = loader.apply(key);
            values.put(key, loaded);
            return loaded;
        }
    }

    public Optional<V> peek(K key) {
        return Optional.ofNullable(values.get(key));
    }
}
