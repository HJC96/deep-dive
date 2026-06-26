package dev.deepdive.jpa.repository;

import dev.deepdive.jpa.core.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
