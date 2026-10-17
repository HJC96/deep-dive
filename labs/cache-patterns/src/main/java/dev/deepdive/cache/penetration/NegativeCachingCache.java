package dev.deepdive.cache.penetration;

import dev.deepdive.cache.infrastructure.Clock;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * "값이 없음"(Optional.empty)도 캐싱해, 존재하지 않는 키 반복 조회가 매번 저장소를 때리는
 * Cache Penetration(관통)을 막는 캐시.
 *
 * <p>negative 엔트리에는 짧은 TTL({@code negativeTtlMillis})을 둔다. TTL이 없으면 나중에 실제로
 * 생성된 값을 empty 마커가 영원히 가린다. TTL이 지나면 다음 조회가 저장소를 다시 확인한다.
 * 값이 있는 엔트리는 이 예제의 관심사가 아니라 만료 없이 둔다.
 * 시계를 주입받아 테스트에서 시간을 결정론적으로 흘린다({@code ExpiringCache}와 같은 방식).
 */
public final class NegativeCachingCache<K, V> {

    // value가 empty면 negative 엔트리, expireAt은 negative 엔트리에만 의미가 있다.
    private record Entry<V>(Optional<V> value, long expireAt) {
    }

    private final ConcurrentHashMap<K, Entry<V>> values = new ConcurrentHashMap<>();
    private final Clock clock;
    private final long negativeTtlMillis;

    public NegativeCachingCache(Clock clock, long negativeTtlMillis) {
        if (negativeTtlMillis <= 0) {
            throw new IllegalArgumentException("negative TTL은 0보다 커야 합니다.");
        }
        this.clock = Objects.requireNonNull(clock, "시계는 필수입니다.");
        this.negativeTtlMillis = negativeTtlMillis;
    }

    public Optional<V> get(K key, Function<K, Optional<V>> loader) {
        long now = clock.nowMillis();
        Entry<V> cached = values.get(key);
        if (cached != null && !isExpiredAt(cached, now)) {
            // empty 마커도 (TTL 안이면) hit으로 취급한다.
            return cached.value();
        }

        Optional<V> loaded = Objects.requireNonNull(loader.apply(key), "loader는 null 대신 Optional.empty()를 반환해야 합니다.");
        values.put(key, new Entry<>(loaded, loaded.isEmpty() ? now + negativeTtlMillis : Long.MAX_VALUE));
        return loaded;
    }

    public boolean hasNegativeEntry(K key) {
        Entry<V> cached = values.get(key);
        return cached != null && cached.value().isEmpty() && !isExpiredAt(cached, clock.nowMillis());
    }

    private boolean isExpiredAt(Entry<V> entry, long now) {
        return now >= entry.expireAt();
    }
}
