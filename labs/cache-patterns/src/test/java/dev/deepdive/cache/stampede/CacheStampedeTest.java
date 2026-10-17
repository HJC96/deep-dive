package dev.deepdive.cache.stampede;

import dev.deepdive.cache.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인기 키가 동시에 miss되거나 같은 시점에 만료될 때 저장소로 몰리는 Cache Stampede와 완화 전략.
 */
class CacheStampedeTest {

    private static final int THREADS = 16;

    @Test
    void 보호_없는_cache_aside는_동시_miss마다_저장소를_로드한다() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        Map<Long, String> cache = new ConcurrentHashMap<>();
        CountDownLatch allArrived = new CountDownLatch(THREADS);

        runConcurrently(() -> {
            if (cache.get(1L) == null) {
                // 모든 스레드가 miss를 확인할 때까지 기다린 뒤 로드 -> stampede 재현
                allArrived.countDown();
                await(allArrived);
                loads.incrementAndGet();
                cache.put(1L, "loaded");
            }
        });

        // 보호 장치가 없으면 동시에 들어온 모든 요청이 저장소를 때린다.
        assertThat(loads.get()).isEqualTo(THREADS);
    }

    @Test
    void single_flight는_동시_miss를_한_번의_로드로_합친다() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        SingleFlightCache<Long, String> cache = new SingleFlightCache<>();

        runConcurrently(() -> cache.get(1L, key -> {
            loads.incrementAndGet();
            return "loaded";
        }));

        // 키별 락 + double-check 덕분에 실제 로드는 1번뿐이다.
        assertThat(loads.get()).isEqualTo(1);
        assertThat(cache.peek(1L)).contains("loaded");
    }

    @Test
    void refresh_ahead는_만료_전에_미리_갱신해_stale_공백이_없다() {
        MutableClock clock = new MutableClock(0);
        AtomicInteger storeLoads = new AtomicInteger();
        ManualExecutor refresher = new ManualExecutor();
        // TTL 1000ms, 80% 경과(=800ms) 시점부터 백그라운드로 미리 갱신
        RefreshAheadCache<Long, String> cache = new RefreshAheadCache<>(clock, 1000, 0.8, refresher);

        // t=0: 최초 로드 (값이 없으니 호출 스레드가 동기 로드)
        cache.get(1L, key -> "v" + storeLoads.incrementAndGet());
        assertThat(cache.loads()).isEqualTo(1);

        // t=500: threshold(800) 전 -> 재로드 없음, 캐시 값 그대로
        clock.advance(500);
        assertThat(cache.get(1L, key -> "v" + storeLoads.incrementAndGet())).isEqualTo("v1");
        assertThat(refresher.pending()).isZero();

        // t=900: threshold 통과, 아직 만료(1000) 전 -> 기존 값을 즉시 주고 갱신은 백그라운드로 제출
        clock.advance(400);
        assertThat(cache.get(1L, key -> "v" + storeLoads.incrementAndGet())).isEqualTo("v1");
        assertThat(cache.loads()).isEqualTo(1);
        assertThat(refresher.pending()).isEqualTo(1);
        assertThat(cache.isRefreshing(1L)).isTrue();

        // 백그라운드 갱신 완료 -> 다음 조회부터 새 값
        refresher.runAll();
        assertThat(cache.loads()).isEqualTo(2);
        assertThat(cache.get(1L, key -> "v" + storeLoads.incrementAndGet())).isEqualTo("v2");

        // 갱신으로 expireAt가 t=1900으로 밀렸으므로, 원래 만료 시점(1000)에도 hard-miss가 없다.
        clock.setTo(1000);
        assertThat(cache.get(1L, key -> "v" + storeLoads.incrementAndGet())).isEqualTo("v2");
        assertThat(cache.loads()).isEqualTo(2);
    }

    @Test
    void refresh_ahead는_threshold_구간에_동시에_몰려도_갱신을_한_번만_제출한다() throws Exception {
        MutableClock clock = new MutableClock(0);
        ManualExecutor refresher = new ManualExecutor();
        AtomicInteger storeLoads = new AtomicInteger();
        RefreshAheadCache<Long, String> cache = new RefreshAheadCache<>(clock, 1000, 0.8, refresher);
        cache.get(1L, key -> "v" + storeLoads.incrementAndGet());
        clock.setTo(900);

        Map<String, Integer> served = new ConcurrentHashMap<>();
        runConcurrently(() -> served.merge(cache.get(1L, key -> "v" + storeLoads.incrementAndGet()), 1, Integer::sum));

        // 모든 요청이 기다림 없이 기존 값을 받고, 갱신 작업은 1개만 제출된다.
        assertThat(served).containsExactly(Map.entry("v1", THREADS));
        assertThat(refresher.pending()).isEqualTo(1);

        refresher.runAll();
        assertThat(storeLoads.get()).isEqualTo(2);
        assertThat(cache.isRefreshing(1L)).isFalse();
    }

    @Test
    void refresh_ahead는_만료된_키의_동시_miss를_한_번의_로드로_합친다() throws Exception {
        MutableClock clock = new MutableClock(0);
        AtomicInteger storeLoads = new AtomicInteger();
        RefreshAheadCache<Long, String> cache = new RefreshAheadCache<>(clock, 1000, 0.8, new ManualExecutor());

        runConcurrently(() -> cache.get(1L, key -> "v" + storeLoads.incrementAndGet()));

        assertThat(storeLoads.get()).isEqualTo(1);
    }

    @Test
    void refresh_ahead는_백그라운드_갱신이_실패해도_기존_값을_유지하고_다시_시도한다() {
        MutableClock clock = new MutableClock(0);
        ManualExecutor refresher = new ManualExecutor();
        RefreshAheadCache<Long, String> cache = new RefreshAheadCache<>(clock, 1000, 0.8, refresher);
        cache.get(1L, key -> "v1");
        clock.setTo(900);

        cache.get(1L, key -> {
            throw new IllegalStateException("store down");
        });
        refresher.runAll();

        assertThat(cache.get(1L, key -> "v2")).isEqualTo("v1");   // 실패해도 기존 값 제공 + 재제출
        assertThat(cache.isRefreshing(1L)).isTrue();
        refresher.runAll();
        assertThat(cache.get(1L, key -> "v3")).isEqualTo("v2");
    }

    /** 제출된 작업을 쌓아 두었다가 테스트가 원할 때 실행하는 Executor. */
    private static final class ManualExecutor implements Executor {

        private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int pending() {
            return tasks.size();
        }

        void runAll() {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                task.run();
            }
        }
    }

    private void runConcurrently(Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                await(start);
                try {
                    task.run();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
