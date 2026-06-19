package dev.deepdive.cache.writethrough;

import dev.deepdive.cache.domain.Product;
import dev.deepdive.cache.infrastructure.Cache;
import dev.deepdive.cache.repository.ProductStore;

import java.util.Optional;

/**
 * Write Through
 *
 * <p>쓰기 요청이 들어오면 저장소(DB 역할)와 캐시를 같은 흐름에서 모두 갱신한 뒤 반환한다.
 * 그래서 save가 끝난 뒤에는 DB와 캐시가 같은 최신 값을 가진다.
 *
 * <p>실무 구현에서는 "캐시에 쓰면 캐시가 DB까지 동기 반영"하는 형태도 있지만,
 * 이 예제에서는 서비스가 store.save(...)와 cache.put(...)을 둘 다 호출해서 흐름을 보여준다.
 */
public final class WriteThroughProductService {

    private final ProductStore store;
    private final Cache<Long, Product> cache;

    public WriteThroughProductService(ProductStore store, Cache<Long, Product> cache) {
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
        // 1. 먼저 실제 저장소(DB 역할)에 변경 내용을 반영한다.
        Product saved = store.save(product);

        // 2. 같은 요청 흐름 안에서 캐시도 최신 값으로 갱신한다.
        //    이 메서드가 반환되면 store와 cache가 둘 다 최신 상태다.
        cache.put(product.id(), saved);
        return saved;
    }
}
