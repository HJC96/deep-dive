package dev.deepdive.paymentsystem.payment.adapter.out.web.product.client;

import dev.deepdive.paymentsystem.payment.domain.Product;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class MockProductClient implements ProductClient {

    @Override
    public Flux<Product> getProducts(long cartId, List<Long> productIds) {
        return Flux.fromIterable(
                productIds.stream()
                        .map(id -> new Product(
                                id,
                                id * 10000,
                                2,
                                "test_product_" + id,
                                1L
                        ))
                        .toList()
        );
    }
}
