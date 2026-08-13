package dev.deepdive.transaction.tcc.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 지갑 참여자가 요청 하나를 어디까지 처리했는지 스스로 적어 두는 로그.
 *
 * <p>좌석 쪽 로그와 규칙이 같다. 로그를 참여자 데이터베이스에 두는 것 자체가 TCC의 전제다. 참여자가
 * 자기 상태를 스스로 책임지므로, 코디네이터 쪽에 모은 공용 로그로는 이 역할을 대신할 수 없다.
 */
@Entity
@Table(name = "wallet_tcc_log")
public class WalletTccLog {

    @Id
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 20)
    private String state;

    protected WalletTccLog() {
    }

    public WalletTccLog(Long requestId, long userId, long amount, String state) {
        this.requestId = requestId;
        this.userId = userId;
        this.amount = amount;
        this.state = state;
    }

    public Long getRequestId() {
        return requestId;
    }

    public long getUserId() {
        return userId;
    }

    public long getAmount() {
        return amount;
    }

    public String getState() {
        return state;
    }

    /** 트랜잭션 안에서 호출하면 더티 체킹으로 UPDATE된다. */
    public void changeState(String state) {
        this.state = state;
    }
}
