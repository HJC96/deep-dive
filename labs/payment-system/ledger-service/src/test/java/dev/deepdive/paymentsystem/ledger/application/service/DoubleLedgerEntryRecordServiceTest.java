package dev.deepdive.paymentsystem.ledger.application.service;

import dev.deepdive.paymentsystem.ledger.application.port.out.DuplicateMessageFilterPort;
import dev.deepdive.paymentsystem.ledger.application.port.out.LoadAccountPort;
import dev.deepdive.paymentsystem.ledger.application.port.out.SaveDoubleLedgerEntryPort;
import dev.deepdive.paymentsystem.ledger.domain.LedgerEventMessage;
import dev.deepdive.paymentsystem.ledger.domain.LedgerEventMessageType;
import dev.deepdive.paymentsystem.ledger.domain.PaymentEventMessage;
import dev.deepdive.paymentsystem.ledger.domain.PaymentEventMessageType;
import dev.deepdive.paymentsystem.ledger.domain.UnbalancedLedgerException;
import dev.deepdive.paymentsystem.ledger.test.LedgerIntegrationTestSupport;
import dev.deepdive.paymentsystem.ledger.test.PaymentEventMessages;
import dev.deepdive.paymentsystem.ledger.test.PaymentEventMessages.Line;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 장부 테이블은 DELETE 금지 트리거 때문에 비울 수 없다. 테스트마다 새 orderId 를 쓰고 그 orderId 로만 확인한다.
 */
class DoubleLedgerEntryRecordServiceTest extends LedgerIntegrationTestSupport {

    @Autowired private DoubleLedgerEntryRecordService doubleLedgerEntryRecordService;
    @Autowired private LoadAccountPort loadAccountPort;
    @Autowired private SaveDoubleLedgerEntryPort saveDoubleLedgerEntryPort;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void should_record_double_ledger_entries_from_the_order_lines_in_the_message() {
        String orderId = UUID.randomUUID().toString();
        PaymentEventMessage message = PaymentEventMessages.paymentConfirmed(orderId,
                new Line(1, 1, 200), new Line(2, 1, 300));

        LedgerEventMessage ledgerEventMessage = doubleLedgerEntryRecordService.recordDoubleLedgerEntry(message);

        assertThat(ledgerEventMessage.type()).isEqualTo(LedgerEventMessageType.SUCCESS);
        assertThat(ledgerEventMessage.payload().get("orderId")).isEqualTo(orderId);
        assertThat(entryCount(orderId)).isEqualTo(4);
        assertThat(signedSum(orderId)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(creditSum(orderId)).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void should_be_recorded_only_once_even_if_called_multiple_times() {
        String orderId = UUID.randomUUID().toString();
        PaymentEventMessage message = PaymentEventMessages.paymentConfirmed(orderId, new Line(1, 1, 200), new Line(2, 1, 300));

        doubleLedgerEntryRecordService.recordDoubleLedgerEntry(message);
        doubleLedgerEntryRecordService.recordDoubleLedgerEntry(message);

        assertThat(entryCount(orderId)).isEqualTo(4);
    }

    @Test
    void should_rely_on_the_unique_constraint_when_the_duplicate_check_is_bypassed() {
        DuplicateMessageFilterPort alwaysNew = message -> false;
        DoubleLedgerEntryRecordService service =
                new DoubleLedgerEntryRecordService(alwaysNew, loadAccountPort, saveDoubleLedgerEntryPort);
        String orderId = UUID.randomUUID().toString();
        PaymentEventMessage message = PaymentEventMessages.paymentConfirmed(orderId, new Line(1, 1, 200), new Line(2, 1, 300));

        LedgerEventMessage first = service.recordDoubleLedgerEntry(message);
        LedgerEventMessage second = service.recordDoubleLedgerEntry(message);

        assertThat(first.type()).isEqualTo(LedgerEventMessageType.SUCCESS);
        assertThat(second.type()).isEqualTo(LedgerEventMessageType.SUCCESS);
        assertThat(entryCount(orderId)).isEqualTo(4);
        assertThat(transactionCount(orderId)).isEqualTo(2);
    }

    /**
     * 같은 메시지가 동시에 여러 번 배달돼도 장부에는 한 번만 기록돼야 한다.
     */
    @RepeatedTest(3)
    void should_record_only_once_when_the_same_message_is_processed_concurrently() throws Exception {
        String orderId = UUID.randomUUID().toString();
        PaymentEventMessage message = PaymentEventMessages.paymentConfirmed(orderId,
                new Line(1, 1, 200), new Line(2, 1, 300), new Line(3, 2, 500));

        List<LedgerEventMessage> results = runConcurrently(6, () -> doubleLedgerEntryRecordService.recordDoubleLedgerEntry(message));

        assertThat(results).hasSize(6).allMatch(it -> it.type() == LedgerEventMessageType.SUCCESS);
        assertThat(transactionCount(orderId)).isEqualTo(3);
        assertThat(entryCount(orderId)).isEqualTo(6);
        assertThat(signedSum(orderId)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_not_record_anything_when_the_ledger_would_not_match_the_payment_total() {
        String orderId = UUID.randomUUID().toString();
        PaymentEventMessage message = PaymentEventMessages.paymentConfirmed(orderId, 1000L, new Line(1, 1, 200));

        assertThatThrownBy(() -> doubleLedgerEntryRecordService.recordDoubleLedgerEntry(message))
                .isInstanceOf(UnbalancedLedgerException.class);
        assertThat(entryCount(orderId)).isZero();
    }

    @Test
    void should_reject_a_legacy_message_without_order_lines() {
        PaymentEventMessage legacy = new PaymentEventMessage(
                PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS,
                Map.of("orderId", UUID.randomUUID().toString()),
                Map.of());

        assertThatThrownBy(() -> doubleLedgerEntryRecordService.recordDoubleLedgerEntry(legacy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paymentOrders");
    }

    @Test
    void ledger_rows_cannot_be_updated_or_deleted_because_the_triggers_are_applied_on_startup() {
        String orderId = UUID.randomUUID().toString();
        doubleLedgerEntryRecordService.recordDoubleLedgerEntry(PaymentEventMessages.paymentConfirmed(orderId, new Line(1, 1, 200)));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                DELETE e FROM ledger_entries e
                JOIN ledger_transactions t ON t.id = e.transaction_id
                WHERE t.order_id = ?""", orderId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE ledger_transactions SET description = 'x' WHERE order_id = ?", orderId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM ledger_transactions WHERE order_id = ?", orderId))
                .hasMessageContaining("append-only");
        assertThat(entryCount(orderId)).isEqualTo(2);
    }

    private int entryCount(String orderId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ledger_entries e
                JOIN ledger_transactions t ON t.id = e.transaction_id
                WHERE t.order_id = ?""", Integer.class, orderId);
    }

    private int transactionCount(String orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_transactions WHERE order_id = ?", Integer.class, orderId);
    }

    private BigDecimal signedSum(String orderId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(CASE e.type WHEN 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
                FROM ledger_entries e
                JOIN ledger_transactions t ON t.id = e.transaction_id
                WHERE t.order_id = ?""", BigDecimal.class, orderId);
    }

    private BigDecimal creditSum(String orderId) {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(e.amount), 0)
                FROM ledger_entries e
                JOIN ledger_transactions t ON t.id = e.transaction_id
                WHERE t.order_id = ? AND e.type = 'CREDIT'""", BigDecimal.class, orderId);
    }

    private static <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executorService.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executorService.shutdownNow();
        }
    }
}
