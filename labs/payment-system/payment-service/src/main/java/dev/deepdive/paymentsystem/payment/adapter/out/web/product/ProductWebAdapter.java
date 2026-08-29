package dev.deepdive.paymentsystem.payment.adapter.out.web.product;

import dev.deepdive.paymentsystem.common.WebAdapter;
import dev.deepdive.paymentsystem.payment.adapter.out.web.product.client.ProductClient;
import dev.deepdive.paymentsystem.payment.application.port.out.LoadProductPort;
import dev.deepdive.paymentsystem.payment.domain.Product;
import reactor.core.publisher.Flux;

import java.util.List;

@WebAdapter
public class ProductWebAdapter implements LoadProductPort {

    private final ProductClient productClient;

    public ProductWebAdapter(ProductClient productClient) {
        this.productClient = productClient;
    }

    @Override
    public Flux<Product> getProducts(long cartId, List<Long> productIds) {
        return productClient.getProducts(cartId, productIds);
    }
}
