package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity;

import dev.deepdive.paymentsystem.wallet.domain.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "wallet_transactions")
public class JpaWalletTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id")
    private Long walletId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    protected JpaWalletTransactionEntity() {
    }

    public JpaWalletTransactionEntity(
            Long walletId,
            BigDecimal amount,
            TransactionType type,
            String orderId,
            String referenceType,
            Long referenceId,
            String idempotencyKey
    ) {
        this.walletId = walletId;
        this.amount = amount;
        this.type = type;
        this.orderId = orderId;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.idempotencyKey = idempotencyKey;
    }

    public Long id() {
        return id;
    }

    public Long walletId() {
        return walletId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public TransactionType type() {
        return type;
    }

    public String orderId() {
        return orderId;
    }

    public String referenceType() {
        return referenceType;
    }

    public Long referenceId() {
        return referenceId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}
