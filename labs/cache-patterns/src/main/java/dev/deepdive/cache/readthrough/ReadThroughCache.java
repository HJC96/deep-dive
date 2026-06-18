package dev.deepdive.cache.readthrough;

import dev.deepdive.cache.infrastructure.Cache;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class ReadThroughCache<K, V> {

    private final Cache<K, V> cache;
    private final Function<K, Optional<V>> loader;

    public ReadThroughCache(Cache<K, V> cache, Function<K, Optional<V>> loader) {
        this.cache = cache;
        this.loader = loader;
    }

    public Optional<V> get(K key) {
        Optional<V> cached = cache.get(key);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<V> loaded = loader.apply(key);
        loaded.ifPresent(value -> cache.put(key, value));
        return loaded;
    }

    public void put(K key, V value) {
        cache.put(key, Objects.requireNonNull(value));
    }

    public void evict(K key) {
        cache.evict(key);
    }
}
