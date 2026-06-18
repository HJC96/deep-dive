package dev.deepdive.cache;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryProductStore implements ProductStore {

    private final Map<Long, Product> products = new LinkedHashMap<>();
    private int readCount;
    private int writeCount;

    public InMemoryProductStore(Product... products) {
        Arrays.stream(products).forEach(product -> this.products.put(product.id(), product));
    }

    @Override
    public Optional<Product> findById(long id) {
        readCount++;
        return Optional.ofNullable(products.get(id));
    }

    @Override
    public Product save(Product product) {
        writeCount++;
        products.put(product.id(), product);
        return product;
    }

    public Optional<Product> storedProduct(long id) {
        return Optional.ofNullable(products.get(id));
    }

    public int readCount() {
        return readCount;
    }

    public int writeCount() {
        return writeCount;
    }
}
