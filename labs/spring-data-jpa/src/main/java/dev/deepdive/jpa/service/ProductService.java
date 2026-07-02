package dev.deepdive.jpa.service;

import dev.deepdive.jpa.core.Product;
import dev.deepdive.jpa.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 서비스. 메서드 하나가 곧 트랜잭션 하나다.
 *
 * <p>{@code @Transactional} 메서드는 끝나면 커밋되고 영속성 컨텍스트가 닫힌다.
 * 그래서 조회 메서드가 돌려준 엔티티는 호출한 쪽에서 보면 detached(준영속) 상태가 된다.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public Long create(String name, int stock) {
        return productRepository.save(new Product(name, stock)).id();
    }

    @Transactional(readOnly = true)
    public Product getById(Long id) {
        return productRepository.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Transactional
    public void decreaseStock(Long id) {
        Product product = productRepository.findById(id).orElseThrow();
        product.decreaseStock();
    }

    /**
     * 호출한 쪽이 들고 있던 (이미 트랜잭션이 끝난) 상품을 다시 저장한다.
     * 그 사이 DB의 version이 올라갔다면 saveAndFlush에서 낙관적 락 예외가 난다.
     */
    @Transactional
    public void decreaseStockAndSave(Product product) {
        product.decreaseStock();
        productRepository.saveAndFlush(product);
    }
}
