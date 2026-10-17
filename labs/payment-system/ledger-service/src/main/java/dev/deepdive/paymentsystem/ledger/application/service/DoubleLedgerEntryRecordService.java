package dev.deepdive.paymentsystem.ledger.application.service;

import dev.deepdive.paymentsystem.ledger.application.exception.LedgerAlreadyRecordedException;
import dev.deepdive.paymentsystem.ledger.application.port.in.DoubleLedgerEntryRecordUseCase;
import dev.deepdive.paymentsystem.ledger.application.port.out.DuplicateMessageFilterPort;
import dev.deepdive.paymentsystem.ledger.application.port.out.LoadAccountPort;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@UseCase
public class DoubleLedgerEntryRecordService implements DoubleLedgerEntryRecordUseCase {

    private static final Logger log = LoggerFactory.getLogger(DoubleLedgerEntryRecordService.class);

    private final DuplicateMessageFilterPort duplicateMessageFilterPort;
    private final LoadAccountPort loadAccountPort;
    private final SaveDoubleLedgerEntryPort saveDoubleLedgerEntryPort;

    public DoubleLedgerEntryRecordService(
            DuplicateMessageFilterPort duplicateMessageFilterPort,
            LoadAccountPort loadAccountPort,
            SaveDoubleLedgerEntryPort saveDoubleLedgerEntryPort
    ) {
        this.duplicateMessageFilterPort = duplicateMessageFilterPort;
        this.loadAccountPort = loadAccountPort;
        this.saveDoubleLedgerEntryPort = saveDoubleLedgerEntryPort;
    }

    @Override
    public LedgerEventMessage recordDoubleLedgerEntry(PaymentEventMessage message) {
        // 빠른 길. 동시에 들어온 중복의 최종 방어선은 ledger_transactions.idempotency_key 의 UNIQUE 제약이다.
        if (duplicateMessageFilterPort.isAlreadyProcess(message)) {
            return createLedgerEventMessage(message);
        }

        DoubleAccountsForLedger doubleAccountsForLedger =
                loadAccountPort.getDoubleAccountsForLedger(FinanceType.PAYMENT_ORDER);
        // 주문 라인은 메시지에 실려 온다. 자기 DB 의 payment_orders 를 읽던 예전 방식은 그 테이블을 채우는 코드가 없어 동작하지 않았다.
        List<PaymentOrder> paymentOrders = message.paymentOrders();

        List<DoubleLedgerEntry> doubleLedgerEntries =
                Ledger.createDoubleLedgerEntry(doubleAccountsForLedger, paymentOrders);

        // 저장 전 불변식: 거래마다 차변 합 == 대변 합, 대변 합 == 결제 총액.
        Ledger.verifyBalanced(doubleLedgerEntries, message.totalAmount());

        try {
            saveDoubleLedgerEntryPort.save(doubleLedgerEntries);
        } catch (LedgerAlreadyRecordedException e) {
            log.info("이미 장부에 기록된 결제라 건너뛴다. orderId={}", message.orderId());
        }

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
