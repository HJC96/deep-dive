package dev.deepdive.cache.avalanche;

import dev.deepdive.cache.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 많은 키가 같은 시각에 만료되는 Cache Avalanche와, TTL 지터로의 완화.
 */
class CacheAvalancheTest {

    private static final int KEYS = 100;
    private static final long BASE_TTL = 1000;

    @Test
    void 고정_TTL은_모든_키를_같은_순간에_만료시킨다() {
        MutableClock clock = new MutableClock(0);
        ExpiringCache<Integer, String> cache = new ExpiringCache<>(clock);

        for (int i = 0; i < KEYS; i++) {
            cache.put(i, "v" + i, BASE_TTL);
        }
        assertThat(cache.activeCount()).isEqualTo(KEYS);

        // 고정 TTL: 정확히 만료 시각에 전부 동시에 죽는다 -> 저장소로 폭주.
        clock.setTo(BASE_TTL);
        assertThat(cache.activeCount()).isZero();
    }

    @Test
    void 지터_TTL은_만료를_시간에_걸쳐_분산시킨다() {
        MutableClock clock = new MutableClock(0);
        ExpiringCache<Integer, String> cache = new ExpiringCache<>(clock);
        Random jitter = new Random(42); // 결정론적 지터

        for (int i = 0; i < KEYS; i++) {
            long ttl = BASE_TTL + jitter.nextInt(500); // [1000, 1500)
            cache.put(i, "v" + i, ttl);
        }

        // 고정 TTL이라면 전부 죽었을 시점(1000)에도 지터 덕분에 아직 살아 있다.
        clock.setTo(BASE_TTL);
        long aliveAt1000 = cache.activeCount();

        // 시간이 흐르며 조금씩 만료된다(한 번에 몰리지 않음 = 분산).
        clock.setTo(BASE_TTL + 250);
        long aliveAt1250 = cache.activeCount();

        // 지터 상한(1500)을 넘기면 결국 모두 만료된다.
        clock.setTo(BASE_TTL + 500);
        long aliveAt1500 = cache.activeCount();

        assertThat(aliveAt1000).isGreaterThan(0);
        assertThat(aliveAt1000).isGreaterThanOrEqualTo(aliveAt1250);
        assertThat(aliveAt1250).isGreaterThan(0);
        assertThat(aliveAt1500).isZero();
    }
}
