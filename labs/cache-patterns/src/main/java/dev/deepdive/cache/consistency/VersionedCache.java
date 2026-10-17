package dev.deepdive.cache.consistency;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 버전(단조 증가하는 정수)을 함께 저장해, 더 오래된 버전의 put을 거부하는 캐시.
 *
 * <p>Cache Aside의 read-after-write race(읽기 스레드가 늦게 stale 값을 다시 put하는 문제)를
 * 막기 위한 안전장치다. 쓰기에서 버전을 올리면, 뒤늦게 도착한 읽기의 낮은 버전 put이 무시된다.
 *
 * <p><b>evict도 버전을 남긴다(tombstone)</b>. evict가 엔트리를 통째로 지우면 비교할 버전이 사라져
 * "쓰기 B가 v2 저장 후 evict → 읽기 A가 뒤늦게 v1 put"이 그대로 받아들여진다.
 * 그래서 evict는 값만 비우고 버전은 남겨 두며, 쓰기 경로는 {@link #evict(Object, long)}로
 * 자신이 저장한 새 버전을 tombstone에 기록한다. 대가로 지운 키도 버전 하나만큼의 메모리를 계속 쓴다
 * (실무에서는 tombstone에 TTL을 둬 race 창보다 길게만 유지한다).
 *
 * <p>버전 비교와 교체는 {@link ConcurrentHashMap#compute}로 키 단위 원자적으로 처리한다.
 */
public final class VersionedCache<K, V> {

    // value == null 이면 tombstone(값은 없고 버전만 기억)
    private record Versioned<V>(long version, V value) {
    }

    private final ConcurrentHashMap<K, Versioned<V>> values = new ConcurrentHashMap<>();
    private final AtomicInteger rejectedPuts = new AtomicInteger();

    public Optional<V> get(K key) {
        Versioned<V> current = values.get(key);
        return current == null ? Optional.empty() : Optional.ofNullable(current.value());
    }

    /**
     * version이 기존 값(또는 tombstone)보다 크거나 같을 때만 반영한다. 더 낮으면 stale로 보고 버린다.
     */
    public void put(K key, long version, V value) {
        if (value == null) {
            throw new IllegalArgumentException("값은 null일 수 없습니다. 무효화는 evict를 쓰세요.");
        }
        values.compute(key, (k, current) -> {
            if (current != null && version < current.version()) {
                rejectedPuts.incrementAndGet();
                return current;
            }
            return new Versioned<>(version, value);
        });
    }

    /**
     * 값을 비우되 지금까지 본 가장 높은 버전을 tombstone으로 남긴다.
     */
    public void evict(K key) {
        values.computeIfPresent(key, (k, current) -> new Versioned<>(current.version(), null));
    }

    /**
     * 쓰기 경로용 무효화: 저장소에 반영한 새 버전을 tombstone으로 남긴다.
     * 이후 그보다 낮은 버전의 put(뒤늦은 읽기)은 거부된다.
     */
    public void evict(K key, long version) {
        values.compute(key, (k, current) -> {
            long tombstoneVersion = current == null ? version : Math.max(current.version(), version);
            return new Versioned<>(tombstoneVersion, null);
        });
    }

    public int rejectedPuts() {
        return rejectedPuts.get();
    }
}
