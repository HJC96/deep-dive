package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity;

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

    private BigDecimal amount;

    @Column(name = "order_id")
    private String orderId;

    protected JpaPaymentOrderEntity() {
    }

    public JpaPaymentOrderEntity(Long id, BigDecimal amount, String orderId) {
        this.id = id;
        this.amount = amount;
        this.orderId = orderId;
    }

    public Long id() {
        return id;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String orderId() {
        return orderId;
    }
}
