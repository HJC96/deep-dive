package dev.deepdive.paymentsystem.wallet.application.exception;

import java.util.Set;

/**
 * 정산 대상 판매자의 지갑이 없다. 예전에는 조용히 건너뛰어 그 판매자 몫의 돈이 사라졌다.
 * 지금은 예외로 메시지 처리를 실패시켜 재시도 후 DLQ 로 보낸다(지갑을 만든 뒤 재처리).
 */
public class WalletNotFoundException extends RuntimeException {

    private final Set<Long> sellerIds;

    public WalletNotFoundException(String orderId, Set<Long> sellerIds) {
        super("결제 (orderId: " + orderId + ") 정산 대상 판매자 " + sellerIds + " 의 지갑이 없습니다.");
        this.sellerIds = Set.copyOf(sellerIds);
    }

    public Set<Long> sellerIds() {
        return sellerIds;
    }
}
