package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class JpaAccountEntity {

    @Id
    private Long id;

    private String name;

    protected JpaAccountEntity() {
    }

    public JpaAccountEntity(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }
}
