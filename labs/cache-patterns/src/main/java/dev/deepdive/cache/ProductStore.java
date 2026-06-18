package dev.deepdive.cache;

import java.util.Optional;

public interface ProductStore {

    Optional<Product> findById(long id);

    Product save(Product product);
}
