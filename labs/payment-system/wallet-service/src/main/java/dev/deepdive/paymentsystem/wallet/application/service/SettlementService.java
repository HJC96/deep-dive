package dev.deepdive.paymentsystem.wallet.application.service;

import dev.deepdive.paymentsystem.wallet.common.UseCase;
import dev.deepdive.paymentsystem.wallet.application.port.in.SettlementUseCase;
import dev.deepdive.paymentsystem.wallet.application.port.out.DuplicateMessageFilterPort;
import dev.deepdive.paymentsystem.wallet.application.port.out.LoadPaymentOrderPort;
import dev.deepdive.paymentsystem.wallet.application.port.out.LoadWalletPort;
import dev.deepdive.paymentsystem.wallet.application.port.out.SaveWalletPort;
import dev.deepdive.paymentsystem.wallet.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.wallet.domain.PaymentOrder;
import dev.deepdive.paymentsystem.wallet.domain.Wallet;
import dev.deepdive.paymentsystem.wallet.domain.WalletEventMessage;
import dev.deepdive.paymentsystem.wallet.domain.WalletEventMessageType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@UseCase
public class SettlementService implements SettlementUseCase {

    private final DuplicateMessageFilterPort duplicateMessageFilterPort;
    private final LoadPaymentOrderPort loadPaymentOrderPort;
    private final LoadWalletPort loadWalletPort;
    private final SaveWalletPort saveWalletPort;

    public SettlementService(
            DuplicateMessageFilterPort duplicateMessageFilterPort,
            LoadPaymentOrderPort loadPaymentOrderPort,
            LoadWalletPort loadWalletPort,
            SaveWalletPort saveWalletPort
    ) {
        this.duplicateMessageFilterPort = duplicateMessageFilterPort;
        this.loadPaymentOrderPort = loadPaymentOrderPort;
        this.loadWalletPort = loadWalletPort;
        this.saveWalletPort = saveWalletPort;
    }

    @Override
    public WalletEventMessage processSettlement(PaymentEventMessage paymentEventMessage) {
        if (duplicateMessageFilterPort.isAlreadyProcess(paymentEventMessage)) {
            return createWalletEventMessage(paymentEventMessage);
        }

        List<PaymentOrder> paymentOrders = loadPaymentOrderPort.getPaymentOrders(paymentEventMessage.orderId());
        Map<Long, List<PaymentOrder>> paymentOrdersBySellerId = paymentOrders.stream()
                .collect(Collectors.groupingBy(PaymentOrder::sellerId));

        List<Wallet> updatedWallets = getUpdatedWallets(paymentOrdersBySellerId);

        saveWalletPort.save(updatedWallets);

        return createWalletEventMessage(paymentEventMessage);
    }

    private WalletEventMessage createWalletEventMessage(PaymentEventMessage paymentEventMessage) {
        return new WalletEventMessage(
                WalletEventMessageType.SUCCESS,
                Map.of("orderId", paymentEventMessage.orderId()),
                Map.of()
        );
    }

    private List<Wallet> getUpdatedWallets(Map<Long, List<PaymentOrder>> paymentOrdersBySellerId) {
        Set<Long> sellerIds = paymentOrdersBySellerId.keySet();

        Set<Wallet> wallets = loadWalletPort.getWallets(sellerIds);

        return wallets.stream()
                .map(wallet -> wallet.calculateBalanceWith(paymentOrdersBySellerId.get(wallet.userId())))
                .toList();
    }
}
