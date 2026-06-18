package dev.deepdive.cache.writebehind;

import dev.deepdive.cache.domain.Product;
import dev.deepdive.cache.infrastructure.Cache;
import dev.deepdive.cache.repository.ProductStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
        cache.put(product.id(), product);
        pendingWrites.put(product.id(), product);
        return product;
    }

    public void flush() {
        for (Product product : new ArrayList<>(pendingWrites.values())) {
            store.save(product);
            pendingWrites.remove(product.id());
        }
    }

    public int pendingWriteCount() {
        return pendingWrites.size();
    }
}
