package dev.deepdive.cache.avalanche;

import dev.deepdive.cache.infrastructure.Clock;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 키마다 만료 시각을 갖는 TTL 캐시. 시계를 주입받아 테스트에서 시간을 결정론적으로 흘릴 수 있다.
 *
 * <p>Cache Avalanche(눈사태): 많은 키가 같은 시각에 만료되면 그 순간 저장소로 요청이 폭주한다.
 * TTL에 지터(jitter)를 주면 만료 시각이 흩어져 폭주가 완화된다. 이 캐시 자체는 TTL만 다루고,
 * 지터 부여는 호출자가 ttlMillis를 다르게 주는 방식으로 실험한다.
 */
public final class ExpiringCache<K, V> {

    private record Entry<V>(V value, long expireAt) {
    }

    private final Map<K, Entry<V>> values = new HashMap<>();
    private final Clock clock;

    public ExpiringCache(Clock clock) {
        this.clock = clock;
    }

    public void put(K key, V value, long ttlMillis) {
        values.put(key, new Entry<>(value, clock.nowMillis() + ttlMillis));
    }

    public Optional<V> get(K key) {
        Entry<V> entry = values.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpiredAt(entry, clock.nowMillis())) {
            values.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public boolean isExpired(K key) {
        Entry<V> entry = values.get(key);
        return entry == null || isExpiredAt(entry, clock.nowMillis());
    }

    /** 현재 시각 기준으로 만료되지 않은(살아 있는) 키의 개수. */
    public long activeCount() {
        long now = clock.nowMillis();
        return values.values().stream().filter(entry -> !isExpiredAt(entry, now)).count();
    }

    private boolean isExpiredAt(Entry<V> entry, long now) {
        return now >= entry.expireAt();
    }
}
