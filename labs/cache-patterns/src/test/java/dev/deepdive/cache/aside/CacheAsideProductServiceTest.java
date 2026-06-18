package dev.deepdive.cache.aside;

import dev.deepdive.cache.domain.Product;
import dev.deepdive.cache.infrastructure.InMemoryCache;
import dev.deepdive.cache.repository.InMemoryProductStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheAsideProductServiceTest {

    @Test
    void cachesProductAfterFirstRead() {
        InMemoryProductStore store = new InMemoryProductStore(new Product(1L, "keyboard", 120_000));
        InMemoryCache<Long, Product> cache = new InMemoryCache<>();
        CacheAsideProductService service = new CacheAsideProductService(store, cache);

        assertThat(service.findById(1L)).contains(new Product(1L, "keyboard", 120_000));
        assertThat(service.findById(1L)).contains(new Product(1L, "keyboard", 120_000));

        assertThat(store.readCount()).isEqualTo(1);
        assertThat(cache.misses()).isEqualTo(1);
        assertThat(cache.hits()).isEqualTo(1);
    }

    @Test
    void evictsCacheAfterWriteSoNextReadReloadsFromStore() {
        InMemoryProductStore store = new InMemoryProductStore(new Product(1L, "keyboard", 120_000));
        InMemoryCache<Long, Product> cache = new InMemoryCache<>();
        CacheAsideProductService service = new CacheAsideProductService(store, cache);

        service.findById(1L);
        service.save(new Product(1L, "keyboard", 99_000));

        assertThat(cache.containsKey(1L)).isFalse();
        assertThat(service.findById(1L)).contains(new Product(1L, "keyboard", 99_000));
        assertThat(store.readCount()).isEqualTo(2);
    }
}
