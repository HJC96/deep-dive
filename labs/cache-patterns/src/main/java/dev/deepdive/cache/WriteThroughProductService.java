package dev.deepdive.cache;

import java.util.Optional;

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
        Product saved = store.save(product);
        cache.put(product.id(), saved);
        return saved;
    }
}
