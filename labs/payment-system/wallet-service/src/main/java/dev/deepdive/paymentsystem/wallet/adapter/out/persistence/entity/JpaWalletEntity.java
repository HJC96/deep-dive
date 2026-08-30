package dev.deepdive.paymentsystem.wallet.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "wallets")
public class JpaWalletEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private BigDecimal balance;

    @Version
    private int version;

    protected JpaWalletEntity() {
    }

    public JpaWalletEntity(Long id, Long userId, BigDecimal balance, int version) {
        this.id = id;
        this.userId = userId;
        this.balance = balance;
        this.version = version;
    }

    public JpaWalletEntity addBalance(BigDecimal amount) {
        return new JpaWalletEntity(id, userId, balance.add(amount), version);
    }

    public Long id() {
        return id;
    }

    public Long userId() {
        return userId;
    }

    public BigDecimal balance() {
        return balance;
    }

    public int version() {
        return version;
    }
}
