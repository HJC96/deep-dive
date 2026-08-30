package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_transactions")
public class JpaLedgerTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    protected JpaLedgerTransactionEntity() {
    }

    public JpaLedgerTransactionEntity(
            String description,
            Long referenceId,
            String referenceType,
            String orderId,
            String idempotencyKey
    ) {
        this.description = description;
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
    }

    public Long id() {
        return id;
    }

    public String description() {
        return description;
    }

    public Long referenceId() {
        return referenceId;
    }

    public String referenceType() {
        return referenceType;
    }

    public String orderId() {
        return orderId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}
