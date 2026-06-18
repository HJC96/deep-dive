package dev.deepdive.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WriteBehindProductServiceTest {

    @Test
    void writesCacheFirstAndFlushesStoreLater() {
        InMemoryProductStore store = new InMemoryProductStore(new Product(1L, "chair", 180_000));
        InMemoryCache<Long, Product> cache = new InMemoryCache<>();
        WriteBehindProductService service = new WriteBehindProductService(store, cache);

        Product updated = service.save(new Product(1L, "chair", 150_000));

        assertThat(updated).isEqualTo(new Product(1L, "chair", 150_000));
        assertThat(service.pendingWriteCount()).isEqualTo(1);
        assertThat(store.writeCount()).isZero();
        assertThat(store.storedProduct(1L)).contains(new Product(1L, "chair", 180_000));
        assertThat(service.findById(1L)).contains(new Product(1L, "chair", 150_000));
        assertThat(store.readCount()).isZero();

        service.flush();

        assertThat(service.pendingWriteCount()).isZero();
        assertThat(store.writeCount()).isEqualTo(1);
        assertThat(store.storedProduct(1L)).contains(new Product(1L, "chair", 150_000));
    }
}
