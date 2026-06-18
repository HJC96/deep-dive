package dev.deepdive.cache.writethrough;

import dev.deepdive.cache.domain.Product;
import dev.deepdive.cache.infrastructure.InMemoryCache;
import dev.deepdive.cache.repository.InMemoryProductStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WriteThroughProductServiceTest {

    @Test
    void writesStoreAndCacheBeforeReturning() {
        InMemoryProductStore store = new InMemoryProductStore();
        InMemoryCache<Long, Product> cache = new InMemoryCache<>();
        WriteThroughProductService service = new WriteThroughProductService(store, cache);

        Product saved = service.save(new Product(1L, "mouse", 49_000));

        assertThat(saved).isEqualTo(new Product(1L, "mouse", 49_000));
        assertThat(store.writeCount()).isEqualTo(1);
        assertThat(cache.containsKey(1L)).isTrue();
        assertThat(service.findById(1L)).contains(new Product(1L, "mouse", 49_000));
        assertThat(store.readCount()).isZero();
    }
}
