package dev.deepdive.cache;

import java.util.Optional;

public final class ReadThroughProductService {

    private final ProductStore store;
    private final ReadThroughCache<Long, Product> products;

    public ReadThroughProductService(ProductStore store, ReadThroughCache<Long, Product> products) {
        this.store = store;
        this.products = products;
    }

    public Optional<Product> findById(long id) {
        return products.get(id);
    }

    public Product save(Product product) {
        Product saved = store.save(product);
        products.evict(product.id());
        return saved;
    }
}
