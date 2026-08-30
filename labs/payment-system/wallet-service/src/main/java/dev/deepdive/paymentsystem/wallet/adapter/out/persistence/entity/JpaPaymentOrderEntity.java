package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "payment_orders")
public class JpaPaymentOrderEntity {

    @Id
    private Long id;

    @Column(name = "seller_id")
    private Long sellerId;

    private BigDecimal amount;

    @Column(name = "order_id")
    private String orderId;

    protected JpaPaymentOrderEntity() {
    }

    public JpaPaymentOrderEntity(Long id, Long sellerId, BigDecimal amount, String orderId) {
        this.id = id;
        this.sellerId = sellerId;
        this.amount = amount;
        this.orderId = orderId;
    }

    public Long id() {
        return id;
    }

    public Long sellerId() {
        return sellerId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String orderId() {
        return orderId;
    }
}
