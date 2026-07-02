package dev.deepdive.jpa.web;

import dev.deepdive.jpa.core.Product;
import dev.deepdive.jpa.service.ProductService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쿼리 모니터링의 관측 대상이 되는 API.
 *
 * <p>각 엔드포인트가 서로 다른 수의 SQL을 유발하므로, Prometheus에서
 * {@code jpa_queries_per_request} 메트릭을 URI 패턴별로 끊어 보면 차이가 드러난다.
 * 예컨대 {@code GET /products}(전체 조회 한 방)와 {@code POST /products/{id}/decrease}
 * (조회 후 UPDATE)의 쿼리 구성이 다르게 잡힌다.
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ProductResponse create(@RequestBody CreateProductRequest request) {
        Long id = productService.create(request.name(), request.stock());
        return ProductResponse.from(productService.getById(id));
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return ProductResponse.from(productService.getById(id));
    }

    @GetMapping
    public List<ProductResponse> findAll() {
        return productService.findAll().stream().map(ProductResponse::from).toList();
    }

    @PostMapping("/{id}/decrease")
    public ProductResponse decrease(@PathVariable Long id) {
        productService.decreaseStock(id);
        return ProductResponse.from(productService.getById(id));
    }

    public record CreateProductRequest(String name, int stock) {
    }

    public record ProductResponse(Long id, String name, int stock, Long version) {
        static ProductResponse from(Product product) {
            return new ProductResponse(product.id(), product.name(), product.stock(), product.version());
        }
    }
}
