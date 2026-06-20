package dev.deepdive.cache.pattern;

import dev.deepdive.cache.domain.Product;
import dev.deepdive.cache.infrastructure.InMemoryCache;
import dev.deepdive.cache.repository.InMemoryProductStore;
import dev.deepdive.cache.writearound.WriteAroundProductService;
import dev.deepdive.cache.writebehind.WriteBehindProductService;
import dev.deepdive.cache.writethrough.WriteThroughProductService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WritePatternTest {

    @Test
    void write_through는_반환_전에_저장소와_캐시를_함께_쓴다() {
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

    @Test
    void write_around는_저장소만_쓰고_이후_읽기에서_캐시를_채운다() {
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

    @Test
    void write_behind는_캐시를_먼저_쓰고_저장소는_flush에서_나중에_쓴다() {
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
