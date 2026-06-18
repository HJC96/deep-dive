package dev.deepdive.cache.infrastructure;

import java.util.Optional;

public interface Cache<K, V> {

    Optional<V> get(K key);

    void put(K key, V value);

    void evict(K key);

    boolean containsKey(K key);

    void clear();
}
