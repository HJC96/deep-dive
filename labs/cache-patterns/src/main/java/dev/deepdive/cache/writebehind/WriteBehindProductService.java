package dev.deepdive.cache.writebehind;

import dev.deepdive.cache.domain.Product;
import dev.deepdive.cache.infrastructure.Cache;
import dev.deepdive.cache.repository.ProductStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Write Behind
 *
 * <p>쓰기 요청이 들어오면 먼저 캐시에 최신 값을 반영하고, 저장소(DB 역할) 쓰기는 나중으로 미룬다.
 * 이 예제에서는 pendingWrites에 모아 두었다가 flush()가 호출될 때 저장소에 반영한다.
 *
 * <p>응답은 빨라질 수 있지만 flush 전에 장애가 나면 저장소에 쓰이지 않은 변경이 사라질 수 있다.
 */
public final class WriteBehindProductService {

    private final ProductStore store;
    private final Cache<Long, Product> cache;
    private final Map<Long, Product> pendingWrites = new LinkedHashMap<>();

    public WriteBehindProductService(ProductStore store, Cache<Long, Product> cache) {
        this.store = store;
        this.cache = cache;
    }

    public Optional<Product> findById(long id) {
        Optional<Product> cached = cache.get(id);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<Product> loaded = store.findById(id);
        loaded.ifPresent(product -> cache.put(id, product));
        return loaded;
    }

    public Product save(Product product) {
        // 1. 먼저 캐시에 최신 값을 반영한다.
        //    그래서 flush 전에도 읽기 요청은 변경된 값을 볼 수 있다.
        cache.put(product.id(), product);

        // 2. 저장소(DB 역할)에 쓸 내용은 큐처럼 모아 둔다.
        //    실제 DB 쓰기는 flush() 시점까지 지연된다.
        pendingWrites.put(product.id(), product);
        return product;
    }

    public void flush() {
        for (Product product : new ArrayList<>(pendingWrites.values())) {
            // 3. 지연해 둔 쓰기를 저장소에 반영한다.
            store.save(product);
            pendingWrites.remove(product.id());
        }
    }

    public int pendingWriteCount() {
        return pendingWrites.size();
    }
}
