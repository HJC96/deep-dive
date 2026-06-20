package dev.deepdive.cache.pattern;

import dev.deepdive.cache.aside.CacheAsideProductService;
import dev.deepdive.cache.domain.Product;
import dev.deepdive.cache.infrastructure.InMemoryCache;
import dev.deepdive.cache.readthrough.ReadThroughCache;
import dev.deepdive.cache.readthrough.ReadThroughProductService;
import dev.deepdive.cache.repository.InMemoryProductStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadPatternTest {

    @Test
    void cache_aside는_서비스가_miss_처리와_캐시_적재를_직접_한다() {
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
    void cache_aside는_쓰기_후_캐시를_비워_다음_읽기에_저장소에서_다시_읽는다() {
        InMemoryProductStore store = new InMemoryProductStore(new Product(1L, "keyboard", 120_000));
        InMemoryCache<Long, Product> cache = new InMemoryCache<>();
        CacheAsideProductService service = new CacheAsideProductService(store, cache);

        service.findById(1L);
        service.save(new Product(1L, "keyboard", 99_000));

        assertThat(cache.containsKey(1L)).isFalse();
        assertThat(service.findById(1L)).contains(new Product(1L, "keyboard", 99_000));
        assertThat(store.readCount()).isEqualTo(2);
    }

    @Test
    void read_through는_캐시_접근_계층이_miss_처리와_loader_호출을_담당한다() {
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

    @Test
    void read_through도_쓰기_후_캐시를_비워_다음_읽기에_loader로_다시_읽는다() {
        InMemoryProductStore store = new InMemoryProductStore(new Product(1L, "monitor", 300_000));
        InMemoryCache<Long, Product> cache = new InMemoryCache<>();
        ReadThroughCache<Long, Product> readThroughCache = new ReadThroughCache<>(cache, store::findById);
        ReadThroughProductService service = new ReadThroughProductService(store, readThroughCache);

        service.findById(1L);
        service.save(new Product(1L, "monitor", 270_000));

        assertThat(cache.containsKey(1L)).isFalse();
        assertThat(service.findById(1L)).contains(new Product(1L, "monitor", 270_000));
        assertThat(store.readCount()).isEqualTo(2);
    }
}
