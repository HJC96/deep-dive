package dev.deepdive.cache.support;

import dev.deepdive.cache.infrastructure.Clock;

/** 테스트에서 시간을 수동으로 흘리는 시계. */
public final class MutableClock implements Clock {

    private long now;

    public MutableClock(long start) {
        this.now = start;
    }

    @Override
    public long nowMillis() {
        return now;
    }

    public void advance(long millis) {
        now += millis;
    }

    public void setTo(long millis) {
        now = millis;
    }
}
