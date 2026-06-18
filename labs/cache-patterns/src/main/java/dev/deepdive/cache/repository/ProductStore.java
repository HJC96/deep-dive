package dev.deepdive.cache.repository;

import dev.deepdive.cache.domain.Product;

import java.util.Optional;

public interface ProductStore {

    Optional<Product> findById(long id);

    Product save(Product product);
}
