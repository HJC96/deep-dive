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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
        name = "wallet_transactions",
        uniqueConstraints = @UniqueConstraint(
                name = JpaWalletTransactionEntity.IDEMPOTENCY_KEY_CONSTRAINT,
                columnNames = "idempotency_key"
        )
)
public class JpaWalletTransactionEntity {

    /**
     * 같은 주문 라인을 두 번 정산하지 못하게 막는 UNIQUE 제약. schema.sql 의 이름과 같아야 한다.
     */
    public static final String IDEMPOTENCY_KEY_CONSTRAINT = "uk_wallet_transactions_idempotency_key";

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

    @Column(name = "idempotency_key", nullable = false)
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
