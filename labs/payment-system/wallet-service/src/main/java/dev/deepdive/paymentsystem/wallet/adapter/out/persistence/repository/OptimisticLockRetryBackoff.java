package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.repository;

import java.util.function.DoubleSupplier;

/**
 * 낙관적 락 충돌 뒤 재시도 대기 시간. 지수 백오프 + 상한 + equal jitter.
 *
 * <pre>
 * exp   = min(maxDelay, baseDelay * 2^(retry-1))
 * delay = exp/2 + random(0 ~ exp/2)
 * </pre>
 * 지수로 늘려 충돌이 몰릴 때 부하를 빠르게 낮추고, 상한으로 한 번의 대기가 무한정 길어지지 않게 하며,
 * 지터로 같은 순간 충돌한 스레드들이 다시 같은 순간에 부딪히지 않게 흩는다. 절반은 고정이라 너무 빨리 재충돌하지도 않는다.
 */
final class OptimisticLockRetryBackoff {

    private final long baseDelayMillis;
    private final long maxDelayMillis;
    private final DoubleSupplier random;

    OptimisticLockRetryBackoff(long baseDelayMillis, long maxDelayMillis, DoubleSupplier random) {
        if (baseDelayMillis <= 0 || maxDelayMillis < baseDelayMillis) {
            throw new IllegalArgumentException("baseDelay > 0 이고 maxDelay >= baseDelay 여야 합니다.");
        }
        this.baseDelayMillis = baseDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
        this.random = random;
    }

    /**
     * @param retry 1부터 시작하는 재시도 번호
     */
    long delayMillis(int retry) {
        if (retry < 1) {
            throw new IllegalArgumentException("retry 는 1 이상이어야 합니다. retry: " + retry);
        }
        // 2^(retry-1) 이 넘치지 않도록 지수를 자른다(상한에 이미 닿은 뒤에는 의미가 없다).
        int exponent = Math.min(retry - 1, 30);
        long exponential = Math.min(maxDelayMillis, baseDelayMillis * (1L << exponent));
        long half = exponential / 2;
        return half + (long) (random.getAsDouble() * (exponential - half));
    }
}
