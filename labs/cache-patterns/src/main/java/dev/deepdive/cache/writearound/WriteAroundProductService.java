package dev.deepdive.cache.writearound;

import dev.deepdive.cache.domain.Product;
import dev.deepdive.cache.infrastructure.Cache;
import dev.deepdive.cache.repository.ProductStore;

import java.util.Optional;

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
        Product saved = store.save(product);
        cache.evict(product.id());
        return saved;
    }
}
