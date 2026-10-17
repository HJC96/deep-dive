package dev.deepdive.cache.stampede;

import dev.deepdive.cache.infrastructure.Clock;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 만료 직전(TTL의 일정 비율 경과 시점)에 미리 값을 갱신해, hard-expiry 순간의 stampede를 피하는 캐시.
 *
 * <ul>
 *   <li>threshold 전: 캐시 값을 그대로 반환한다.</li>
 *   <li>threshold ~ 만료 전: 지금 가진 값을 <b>즉시</b> 반환하고, 갱신은 주입받은 {@link Executor}에 맡긴다.
 *       같은 키의 갱신은 한 번에 하나만 돈다(이미 갱신 중이면 다시 제출하지 않음).</li>
 *   <li>값이 없거나 이미 만료: 반환할 값이 없으므로 호출 스레드가 동기로 로드한다.
 *       같은 키의 동시 miss는 키별 락 + double-check로 1회 로드로 합친다.</li>
 * </ul>
 *
 * <p>백그라운드 갱신이 실패하면 기존 값을 그대로 두고(만료까지 계속 제공), 다음 조회가 다시 갱신을 시도한다.
 * 테스트는 제출된 작업을 직접 실행하는 Executor를 넣어 "언제 갱신이 끝나는지"를 결정론적으로 제어한다.
 */
public final class RefreshAheadCache<K, V> {

    private record Entry<V>(V value, long loadedAt, long expireAt) {
    }

    private final ConcurrentHashMap<K, Entry<V>> values = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, Object> locks = new ConcurrentHashMap<>();
    private final Set<K> refreshing = ConcurrentHashMap.newKeySet();
    private final AtomicInteger loads = new AtomicInteger();
    private final Clock clock;
    private final long ttlMillis;
    private final double refreshThreshold;
    private final Executor refreshExecutor;

    public RefreshAheadCache(Clock clock, long ttlMillis, double refreshThreshold, Executor refreshExecutor) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("TTL은 0보다 커야 합니다.");
        }
        if (refreshThreshold <= 0 || refreshThreshold > 1) {
            throw new IllegalArgumentException("갱신 기준 비율은 0 초과 1 이하여야 합니다.");
        }
        this.clock = Objects.requireNonNull(clock, "시계는 필수입니다.");
        this.ttlMillis = ttlMillis;
        this.refreshThreshold = refreshThreshold;
        this.refreshExecutor = Objects.requireNonNull(refreshExecutor, "갱신 Executor는 필수입니다.");
    }

    public V get(K key, Function<K, V> loader) {
        long now = clock.nowMillis();
        Entry<V> entry = values.get(key);

        if (entry == null || now >= entry.expireAt()) {
            return loadSynchronously(key, loader);
        }

        long age = now - entry.loadedAt();
        if (age >= ttlMillis * refreshThreshold) {
            // 아직 만료 전이므로 가진 값을 바로 주고, 갱신은 백그라운드로 한 번만 건다.
            scheduleRefresh(key, loader);
        }
        return entry.value();
    }

    private V loadSynchronously(K key, Function<K, V> loader) {
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            // 락을 기다리는 사이 다른 스레드가 이미 채웠을 수 있으므로 다시 확인한다.
            Entry<V> again = values.get(key);
            if (again != null && clock.nowMillis() < again.expireAt()) {
                return again.value();
            }
            return load(key, loader);
        }
    }

    private void scheduleRefresh(K key, Function<K, V> loader) {
        if (!refreshing.add(key)) {
            return;   // 이미 이 키를 갱신 중
        }
        try {
            refreshExecutor.execute(() -> {
                try {
                    load(key, loader);
                } catch (RuntimeException e) {
                    // 갱신 실패: 기존 값을 유지하고 다음 조회가 다시 시도하게 둔다.
                } finally {
                    refreshing.remove(key);
                }
            });
        } catch (RuntimeException e) {
            // Executor가 작업을 거절하면 갱신 중 표시를 풀어 다음 조회가 다시 시도하게 한다.
            refreshing.remove(key);
        }
    }

    private V load(K key, Function<K, V> loader) {
        long loadedAt = clock.nowMillis();
        V loaded = loader.apply(key);
        loads.incrementAndGet();
        values.put(key, new Entry<>(loaded, loadedAt, loadedAt + ttlMillis));
        return loaded;
    }

    public int loads() {
        return loads.get();
    }

    public boolean isRefreshing(K key) {
        return refreshing.contains(key);
    }
}
