package dev.deepdive.cache.stampede;

import dev.deepdive.cache.infrastructure.Clock;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 만료 직전(TTL의 일정 비율 경과 시점)에 미리 값을 갱신해, hard-expiry 순간의 stampede를 피하는 캐시.
 *
 * <p>refreshThreshold(예: 0.8)를 넘긴 조회는 아직 만료 전이라 즉시 응답하면서도 백그라운드로
 * 값을 새로 적재한다. 이 예제는 결정론적 검증을 위해 동기적으로 refresh한다.
 */
public final class RefreshAheadCache<K, V> {

    private record Entry<V>(V value, long loadedAt, long expireAt) {
    }

    private final Map<K, Entry<V>> values = new HashMap<>();
    private final Clock clock;
    private final long ttlMillis;
    private final double refreshThreshold;
    private int loads;

    public RefreshAheadCache(Clock clock, long ttlMillis, double refreshThreshold) {
        this.clock = clock;
        this.ttlMillis = ttlMillis;
        this.refreshThreshold = refreshThreshold;
    }

    public V get(K key, Function<K, V> loader) {
        long now = clock.nowMillis();
        Entry<V> entry = values.get(key);

        if (entry == null || now >= entry.expireAt()) {
            return load(key, loader, now);
        }

        long age = now - entry.loadedAt();
        if (age >= ttlMillis * refreshThreshold) {
            // 아직 만료 전이라 stale gap 없이 미리 갱신한다.
            return load(key, loader, now);
        }

        return entry.value();
    }

    private V load(K key, Function<K, V> loader, long now) {
        V loaded = loader.apply(key);
        loads++;
        values.put(key, new Entry<>(loaded, now, now + ttlMillis));
        return loaded;
    }

    public int loads() {
        return loads;
    }
}
