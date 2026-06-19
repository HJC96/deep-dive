package dev.deepdive.cache.writearound;

import dev.deepdive.cache.domain.Product;
import dev.deepdive.cache.infrastructure.Cache;
import dev.deepdive.cache.repository.ProductStore;

import java.util.Optional;

/**
 * Write Around
 *
 * <p>쓰기 요청은 캐시를 지나치고 저장소(DB 역할)에만 바로 반영한다.
 * 캐시는 즉시 갱신하지 않고, 나중에 읽기 요청이 들어와 cache miss가 발생하면 저장소에서 읽어와 다시 채운다.
 *
 * <p>자주 읽히지 않는 데이터를 매번 캐시에 넣지 않아도 되므로 캐시 오염을 줄일 수 있다.
 */
public final class WriteAroundProductService {

    private final ProductStore store;
    private final Cache<Long, Product> cache;

    public WriteAroundProductService(ProductStore store, Cache<Long, Product> cache) {
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
        // 1. 변경 내용은 저장소(DB 역할)에만 반영한다.
        Product saved = store.save(product);

        // 2. 캐시에 오래된 값이 남아 있을 수 있으므로 제거한다.
        //    새 값은 다음 findById에서 cache miss가 난 뒤 저장소에서 읽어오며 캐시에 채워진다.
        cache.evict(product.id());
        return saved;
    }
}
