package dev.deepdive.transaction.tcc.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 지갑. 쓸 수 있는 잔액({@code balance})과 얼려 둔 돈({@code frozen})을 칸으로 나눠 둔다.
 *
 * <p>Try에서 {@code balance}를 그대로 두고 {@code frozen}만 올리면 안 된다. Try 커밋 직후에는 락이
 * 없어서 다음 요청이 같은 돈을 그대로 보게 된다. {@code balance}에서 먼저 빼고 {@code frozen}으로
 * 옮겨 둬야 이중 지출이 나지 않는다.
 */
@Entity
@Table(name = "wallet")
public class Wallet {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private long balance;

    @Column(nullable = false)
    private long frozen;

    protected Wallet() {
    }

    public Wallet(Long userId, long balance) {
        this.userId = userId;
        this.balance = balance;
        this.frozen = 0L;
    }

    /** 돈을 얼려 둔다. 잔액이 모자라면 false. */
    public boolean freeze(long amount) {
        if (balance < amount) {
            return false;
        }
        balance -= amount;
        frozen += amount;
        return true;
    }

    /** 얼려 둔 돈을 확정으로 지운다. balance는 Try에서 이미 빠졌으니 frozen만 줄이면 끝이다. */
    public void confirmFrozen(long amount) {
        frozen -= amount;
    }

    /** 얼려 둔 돈을 잔액으로 되돌린다. */
    public void releaseFrozen(long amount) {
        balance += amount;
        frozen -= amount;
    }

    public Long getUserId() {
        return userId;
    }

    public long getBalance() {
        return balance;
    }

    public long getFrozen() {
        return frozen;
    }
}
