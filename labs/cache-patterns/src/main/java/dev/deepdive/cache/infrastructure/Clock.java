package dev.deepdive.cache.infrastructure;

@FunctionalInterface
public interface Clock {

    long nowMillis();
}
