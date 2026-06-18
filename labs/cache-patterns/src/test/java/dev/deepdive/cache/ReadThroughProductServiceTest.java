package dev.deepdive.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadThroughProductServiceTest {

    @Test
    void cacheOwnsLoadingBehaviorOnMiss() {
        InMemoryProductStore store = new InMemoryProductStore(new Product(1L, "monitor", 300_000));
        InMemoryCache<Long, Product> cache = new InMemoryCache<>();
        ReadThroughCache<Long, Product> readThroughCache = new ReadThroughCache<>(cache, store::findById);
        ReadThroughProductService service = new ReadThroughProductService(store, readThroughCache);

        assertThat(service.findById(1L)).contains(new Product(1L, "monitor", 300_000));
        assertThat(service.findById(1L)).contains(new Product(1L, "monitor", 300_000));

        assertThat(store.readCount()).isEqualTo(1);
        assertThat(cache.misses()).isEqualTo(1);
        assertThat(cache.hits()).isEqualTo(1);
    }
}
