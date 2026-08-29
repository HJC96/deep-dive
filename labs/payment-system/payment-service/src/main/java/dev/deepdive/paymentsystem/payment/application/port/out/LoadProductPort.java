package dev.deepdive.paymentsystem.payment.application.port.out;

import dev.deepdive.paymentsystem.payment.domain.Product;
import reactor.core.publisher.Flux;

import java.util.List;

public interface LoadProductPort {

    Flux<Product> getProducts(long cartId, List<Long> productIds);
}
