package dev.deepdive.cache.consistency;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 버전(단조 증가하는 정수)을 함께 저장해, 더 오래된 버전의 put을 거부하는 캐시.
 *
 * <p>Cache Aside의 read-after-write race(읽기 스레드가 늦게 stale 값을 다시 put하는 문제)를
 * 막기 위한 안전장치다. 쓰기에서 버전을 올리면, 뒤늦게 도착한 읽기의 낮은 버전 put이 무시된다.
 */
public final class VersionedCache<K, V> {

    private record Versioned<V>(long version, V value) {
    }

    private final Map<K, Versioned<V>> values = new HashMap<>();
    private int rejectedPuts;

    public Optional<V> get(K key) {
        Versioned<V> current = values.get(key);
        return current == null ? Optional.empty() : Optional.of(current.value());
    }

    /**
     * version이 기존 값보다 크거나 같을 때만 반영한다. 더 낮으면 stale로 보고 버린다.
     */
    public void put(K key, long version, V value) {
        Versioned<V> current = values.get(key);
        if (current != null && version < current.version()) {
            rejectedPuts++;
            return;
        }
        values.put(key, new Versioned<>(version, value));
    }

    public void evict(K key) {
        values.remove(key);
    }

    public int rejectedPuts() {
        return rejectedPuts;
    }
}
