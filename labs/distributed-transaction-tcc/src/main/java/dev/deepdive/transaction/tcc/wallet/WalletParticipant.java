package dev.deepdive.transaction.tcc.wallet;

import static dev.deepdive.transaction.tcc.TccState.CANCELLED;
import static dev.deepdive.transaction.tcc.TccState.CONFIRMED;
import static dev.deepdive.transaction.tcc.TccState.TRIED;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지갑 참여자. Try에서 돈을 얼려 두고, Confirm에서 실제로 빠져나가게 하고, Cancel에서 얼린 돈을 녹인다.
 *
 * <p>좌석 참여자와 같은 규칙으로 움직인다. 세 단계가 각각 자기 로컬 트랜잭션을 곧바로 커밋하고,
 * 어디까지 갔는지는 {@link WalletTccLog}에 적어 둔다.
 *
 * <p>{@code @Transactional}의 트랜잭션 매니저가 좌석 쪽과 다르다는 점이 중요하다. 좌석 커밋과 지갑
 * 커밋은 서로 다른 트랜잭션이고, 하나가 실패해도 다른 하나가 자동으로 되돌아가지 않는다.
 */
@Service
public class WalletParticipant {

    private final WalletRepository wallets;
    private final WalletTccLogRepository logs;

    public WalletParticipant(WalletRepository wallets, WalletTccLogRepository logs) {
        this.wallets = wallets;
        this.logs = logs;
    }

    /** 돈을 얼려 둔다. 얼렸으면 true, 잔액이 모자라거나 이미 취소된 요청이면 false. */
    @Transactional("walletTransactionManager")
    public boolean tryFreeze(long requestId, long userId, long amount) {
        WalletTccLog recorded = logs.findById(requestId).orElse(null);
        if (recorded != null) {
            // 취소가 먼저 지나갔다면 여기서 거부해야 아무도 녹이지 않는 돈이 남지 않는다.
            return !CANCELLED.equals(recorded.getState());
        }

        Wallet wallet = wallets.findById(userId).orElseThrow();
        if (!wallet.freeze(amount)) {
            return false;
        }
        wallets.save(wallet);

        logs.save(new WalletTccLog(requestId, userId, amount, TRIED));
        return true;
    }

    /** 얼려 둔 돈을 확정으로 지운다. TRIED가 아니면 아무것도 하지 않는다. */
    @Transactional("walletTransactionManager")
    public void confirm(long requestId) {
        WalletTccLog log = logs.findById(requestId).orElse(null);
        if (log == null || !TRIED.equals(log.getState())) {
            return;
        }

        Wallet wallet = wallets.findById(log.getUserId()).orElseThrow();
        wallet.confirmFrozen(log.getAmount());
        wallets.save(wallet);

        log.changeState(CONFIRMED);
        logs.save(log);
    }

    /**
     * 얼려 둔 돈을 잔액으로 되돌린다. TRIED가 아니면 자원은 건드리지 않는다.
     *
     * <p>Try 기록이 아예 없으면 빈 취소다. 자원 대신 CANCELLED만 적어 둬야 뒤늦은 Try가 거부된다.
     */
    @Transactional("walletTransactionManager")
    public void cancel(long requestId) {
        WalletTccLog log = logs.findById(requestId).orElse(null);
        if (log == null) {
            logs.save(new WalletTccLog(requestId, 0L, 0L, CANCELLED));
            return;
        }
        if (!TRIED.equals(log.getState())) {
            return;
        }

        Wallet wallet = wallets.findById(log.getUserId()).orElseThrow();
        wallet.releaseFrozen(log.getAmount());
        wallets.save(wallet);

        log.changeState(CANCELLED);
        logs.save(log);
    }
}
