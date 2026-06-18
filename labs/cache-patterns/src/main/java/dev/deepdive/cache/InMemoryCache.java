package dev.deepdive.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryCache<K, V> implements Cache<K, V> {

    private final Map<K, V> values = new LinkedHashMap<>();
    private int hits;
    private int misses;

    @Override
    public Optional<V> get(K key) {
        if (values.containsKey(key)) {
            hits++;
            return Optional.of(values.get(key));
        }

        misses++;
        return Optional.empty();
    }

    @Override
    public void put(K key, V value) {
        values.put(key, Objects.requireNonNull(value));
    }

    @Override
    public void evict(K key) {
        values.remove(key);
    }

    @Override
    public boolean containsKey(K key) {
        return values.containsKey(key);
    }

    @Override
    public void clear() {
        values.clear();
        hits = 0;
        misses = 0;
    }

    public int hits() {
        return hits;
    }

    public int misses() {
        return misses;
    }

    public int size() {
        return values.size();
    }
}
