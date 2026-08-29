package dev.deepdive.paymentsystem.payment.adapter.out.web.product.client;

import dev.deepdive.paymentsystem.payment.domain.Product;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ProductClient {

    Flux<Product> getProducts(long cartId, List<Long> productIds);
}
