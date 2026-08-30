package dev.deepdive.paymentsystem.ledger.application.service;

import dev.deepdive.paymentsystem.ledger.application.port.in.DoubleLedgerEntryRecordUseCase;
import dev.deepdive.paymentsystem.ledger.application.port.out.DuplicateMessageFilterPort;
import dev.deepdive.paymentsystem.ledger.application.port.out.LoadAccountPort;
import dev.deepdive.paymentsystem.ledger.application.port.out.LoadPaymentOrderPort;
import dev.deepdive.paymentsystem.ledger.application.port.out.SaveDoubleLedgerEntryPort;
import dev.deepdive.paymentsystem.ledger.common.UseCase;
import dev.deepdive.paymentsystem.ledger.domain.DoubleAccountsForLedger;
import dev.deepdive.paymentsystem.ledger.domain.DoubleLedgerEntry;
import dev.deepdive.paymentsystem.ledger.domain.FinanceType;
import dev.deepdive.paymentsystem.ledger.domain.Ledger;
import dev.deepdive.paymentsystem.ledger.domain.LedgerEventMessage;
import dev.deepdive.paymentsystem.ledger.domain.LedgerEventMessageType;
import dev.deepdive.paymentsystem.ledger.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.ledger.domain.PaymentOrder;

import java.util.List;
import java.util.Map;

@UseCase
public class DoubleLedgerEntryRecordService implements DoubleLedgerEntryRecordUseCase {

    private final DuplicateMessageFilterPort duplicateMessageFilterPort;
    private final LoadAccountPort loadAccountPort;
    private final LoadPaymentOrderPort loadPaymentOrderPort;
    private final SaveDoubleLedgerEntryPort saveDoubleLedgerEntryPort;

    public DoubleLedgerEntryRecordService(
            DuplicateMessageFilterPort duplicateMessageFilterPort,
            LoadAccountPort loadAccountPort,
            LoadPaymentOrderPort loadPaymentOrderPort,
            SaveDoubleLedgerEntryPort saveDoubleLedgerEntryPort
    ) {
        this.duplicateMessageFilterPort = duplicateMessageFilterPort;
        this.loadAccountPort = loadAccountPort;
        this.loadPaymentOrderPort = loadPaymentOrderPort;
        this.saveDoubleLedgerEntryPort = saveDoubleLedgerEntryPort;
    }

    @Override
    public LedgerEventMessage recordDoubleLedgerEntry(PaymentEventMessage message) {
        if (duplicateMessageFilterPort.isAlreadyProcess(message)) {
            return createLedgerEventMessage(message);
        }

        DoubleAccountsForLedger doubleAccountsForLedger =
                loadAccountPort.getDoubleAccountsForLedger(FinanceType.PAYMENT_ORDER);
        List<PaymentOrder> paymentOrders = loadPaymentOrderPort.getPaymentOrders(message.orderId());

        List<DoubleLedgerEntry> doubleLedgerEntries =
                Ledger.createDoubleLedgerEntry(doubleAccountsForLedger, paymentOrders);

        saveDoubleLedgerEntryPort.save(doubleLedgerEntries);

        return createLedgerEventMessage(message);
    }

    private LedgerEventMessage createLedgerEventMessage(PaymentEventMessage message) {
        return new LedgerEventMessage(
                LedgerEventMessageType.SUCCESS,
                Map.of("orderId", message.orderId()),
                Map.of()
        );
    }
}
