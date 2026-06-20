package dev.deepdive.cache.stampede;

import dev.deepdive.cache.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
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
        // TTL 1000ms, 80% 경과(=800ms) 시점부터 미리 갱신
        RefreshAheadCache<Long, String> cache = new RefreshAheadCache<>(clock, 1000, 0.8);

        // t=0: 최초 로드
        cache.get(1L, key -> "v" + storeLoads.incrementAndGet());
        assertThat(cache.loads()).isEqualTo(1);

        // t=500: threshold(800) 전 -> 재로드 없음, 캐시 값 그대로
        clock.advance(500);
        assertThat(cache.get(1L, key -> "v" + storeLoads.incrementAndGet())).isEqualTo("v1");
        assertThat(cache.loads()).isEqualTo(1);

        // t=900: threshold 통과, 아직 만료(1000) 전 -> 미리 갱신
        clock.advance(400);
        assertThat(cache.get(1L, key -> "v" + storeLoads.incrementAndGet())).isEqualTo("v2");
        assertThat(cache.loads()).isEqualTo(2);

        // 갱신으로 expireAt가 t=1900으로 밀렸으므로, 원래 만료 시점(1000)에도 hard-miss가 없다.
        clock.setTo(1000);
        assertThat(cache.get(1L, key -> "v" + storeLoads.incrementAndGet())).isEqualTo("v2");
        assertThat(cache.loads()).isEqualTo(2);
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
