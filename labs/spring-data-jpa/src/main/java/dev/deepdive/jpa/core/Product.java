package dev.deepdive.jpa.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Objects;

/**
 * Spring Data JPA 기능 실험용 엔티티.
 *
 * <p>{@code @Version} 컬럼이 있으면 JPA가 UPDATE 시 {@code WHERE id=? AND version=?} 조건을 붙이고,
 * 영향받은 행이 0이면(= 그 사이 누군가 먼저 수정해 버전이 올라감) 낙관적 락 예외를 던진다.
 * 커밋/flush 때마다 version은 1씩 자동 증가한다.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int stock;

    @Version
    private Long version;

    protected Product() {
    }

    public Product(String name, int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("재고는 음수일 수 없습니다.");
        }
        this.name = Objects.requireNonNull(name, "상품 이름은 필수입니다.");
        this.stock = stock;
    }

    public void decreaseStock() {
        if (stock <= 0) {
            throw new IllegalStateException("재고가 없습니다.");
        }
        stock--;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int stock() {
        return stock;
    }

    public Long version() {
        return version;
    }
}
