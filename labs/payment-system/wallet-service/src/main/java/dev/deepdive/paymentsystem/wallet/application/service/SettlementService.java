package dev.deepdive.paymentsystem.wallet.application.service;

import dev.deepdive.paymentsystem.wallet.application.exception.SettlementAlreadyProcessedException;
import dev.deepdive.paymentsystem.wallet.application.exception.WalletNotFoundException;
import dev.deepdive.paymentsystem.wallet.application.port.in.SettlementUseCase;
import dev.deepdive.paymentsystem.wallet.application.port.out.DuplicateMessageFilterPort;
import dev.deepdive.paymentsystem.wallet.application.port.out.LoadWalletPort;
import dev.deepdive.paymentsystem.wallet.application.port.out.SaveWalletPort;
import dev.deepdive.paymentsystem.wallet.common.UseCase;
import dev.deepdive.paymentsystem.wallet.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.wallet.domain.PaymentOrder;
import dev.deepdive.paymentsystem.wallet.domain.Wallet;
import dev.deepdive.paymentsystem.wallet.domain.WalletEventMessage;
import dev.deepdive.paymentsystem.wallet.domain.WalletEventMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@UseCase
public class SettlementService implements SettlementUseCase {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final DuplicateMessageFilterPort duplicateMessageFilterPort;
    private final LoadWalletPort loadWalletPort;
    private final SaveWalletPort saveWalletPort;

    public SettlementService(
            DuplicateMessageFilterPort duplicateMessageFilterPort,
            LoadWalletPort loadWalletPort,
            SaveWalletPort saveWalletPort
    ) {
        this.duplicateMessageFilterPort = duplicateMessageFilterPort;
        this.loadWalletPort = loadWalletPort;
        this.saveWalletPort = saveWalletPort;
    }

    @Override
    public WalletEventMessage processSettlement(PaymentEventMessage paymentEventMessage) {
        // 빠른 길: 이미 처리한 메시지면 바로 성공을 돌려준다. 동시에 들어온 중복은 이 확인을 함께 통과할 수 있으므로
        // 최종 방어선은 wallet_transactions.idempotency_key 의 UNIQUE 제약이다(아래 SettlementAlreadyProcessedException).
        if (duplicateMessageFilterPort.isAlreadyProcess(paymentEventMessage)) {
            return createWalletEventMessage(paymentEventMessage);
        }

        // 주문 라인은 메시지에 실려 온다. 자기 DB 의 payment_orders 를 읽던 예전 방식은 그 테이블을 채우는 코드가 없어 동작하지 않았다.
        List<PaymentOrder> paymentOrders = paymentEventMessage.paymentOrders();
        Map<Long, List<PaymentOrder>> paymentOrdersBySellerId = paymentOrders.stream()
                .collect(Collectors.groupingBy(PaymentOrder::sellerId));

        List<Wallet> updatedWallets = getUpdatedWallets(paymentEventMessage.orderId(), paymentOrdersBySellerId);

        try {
            saveWalletPort.save(updatedWallets);
        } catch (SettlementAlreadyProcessedException e) {
            log.info("이미 정산된 결제라 건너뛴다. orderId={}", paymentEventMessage.orderId());
        }

        return createWalletEventMessage(paymentEventMessage);
    }

    private WalletEventMessage createWalletEventMessage(PaymentEventMessage paymentEventMessage) {
        return new WalletEventMessage(
                WalletEventMessageType.SUCCESS,
                Map.of("orderId", paymentEventMessage.orderId()),
                Map.of()
        );
    }

    private List<Wallet> getUpdatedWallets(String orderId, Map<Long, List<PaymentOrder>> paymentOrdersBySellerId) {
        Set<Long> sellerIds = paymentOrdersBySellerId.keySet();

        Set<Wallet> wallets = loadWalletPort.getWallets(sellerIds);

        // 지갑이 없는 판매자가 있으면 정산 전체를 실패시킨다. 일부만 정산하고 성공을 알리면 그 몫이 조용히 사라진다.
        Set<Long> missingSellerIds = new HashSet<>(sellerIds);
        wallets.forEach(wallet -> missingSellerIds.remove(wallet.userId()));
        if (!missingSellerIds.isEmpty()) {
            throw new WalletNotFoundException(orderId, missingSellerIds);
        }

        return wallets.stream()
                .map(wallet -> wallet.calculateBalanceWith(paymentOrdersBySellerId.get(wallet.userId())))
                .toList();
    }
}
