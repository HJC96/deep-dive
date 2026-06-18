package dev.deepdive.cache.writearound;

import dev.deepdive.cache.domain.Product;
import dev.deepdive.cache.infrastructure.InMemoryCache;
import dev.deepdive.cache.repository.InMemoryProductStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WriteAroundProductServiceTest {

    @Test
    void writesStoreOnlyAndWarmsCacheOnLaterRead() {
        InMemoryProductStore store = new InMemoryProductStore();
        InMemoryCache<Long, Product> cache = new InMemoryCache<>();
        WriteAroundProductService service = new WriteAroundProductService(store, cache);

        service.save(new Product(1L, "webcam", 89_000));

        assertThat(store.writeCount()).isEqualTo(1);
        assertThat(cache.containsKey(1L)).isFalse();

        assertThat(service.findById(1L)).contains(new Product(1L, "webcam", 89_000));
        assertThat(service.findById(1L)).contains(new Product(1L, "webcam", 89_000));

        assertThat(store.readCount()).isEqualTo(1);
        assertThat(cache.hits()).isEqualTo(1);
    }
}
