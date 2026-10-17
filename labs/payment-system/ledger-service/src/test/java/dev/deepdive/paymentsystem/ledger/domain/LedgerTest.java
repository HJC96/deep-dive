package dev.deepdive.paymentsystem.ledger.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerTest {

    private static final Account REVENUE = new Account(1, "REVENUE");
    private static final Account ITEM_BUYER = new Account(2, "ITEM_BUYER");
    private static final DoubleAccountsForLedger ACCOUNTS = new DoubleAccountsForLedger(REVENUE, ITEM_BUYER);

    @Test
    void creates_one_balanced_pair_per_order_line() {
        List<DoubleLedgerEntry> entries = Ledger.createDoubleLedgerEntry(ACCOUNTS, List.of(
                new PaymentOrder(1, 200, "order"), new PaymentOrder(2, 300, "order")));

        assertThat(entries).hasSize(2);
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.credit().type()).isEqualTo(LedgerEntryType.CREDIT);
            assertThat(entry.credit().account()).isEqualTo(REVENUE);
            assertThat(entry.debit().type()).isEqualTo(LedgerEntryType.DEBIT);
            assertThat(entry.debit().account()).isEqualTo(ITEM_BUYER);
        });
        assertThatCode(() -> Ledger.verifyBalanced(entries, 500)).doesNotThrowAnyException();
    }

    @Test
    void double_entry_rejects_mismatched_amounts() {
        assertThatThrownBy(() -> new DoubleLedgerEntry(
                new LedgerEntry(REVENUE, 100, LedgerEntryType.CREDIT),
                new LedgerEntry(ITEM_BUYER, 90, LedgerEntryType.DEBIT),
                transaction(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void double_entry_rejects_wrong_directions_even_if_amounts_match() {
        // 예전 검사는 금액만 봐서 "대변 두 줄"도 통과했다.
        assertThatThrownBy(() -> new DoubleLedgerEntry(
                new LedgerEntry(REVENUE, 100, LedgerEntryType.CREDIT),
                new LedgerEntry(ITEM_BUYER, 100, LedgerEntryType.CREDIT),
                transaction(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void double_entry_rejects_non_positive_amount_and_same_account() {
        assertThatThrownBy(() -> new DoubleLedgerEntry(
                new LedgerEntry(REVENUE, 0, LedgerEntryType.CREDIT),
                new LedgerEntry(ITEM_BUYER, 0, LedgerEntryType.DEBIT),
                transaction(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DoubleLedgerEntry(
                new LedgerEntry(REVENUE, 100, LedgerEntryType.CREDIT),
                new LedgerEntry(REVENUE, 100, LedgerEntryType.DEBIT),
                transaction(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assert_balanced_compares_debit_and_credit_sums_by_direction() {
        assertThatCode(() -> Ledger.assertBalanced("tx", List.of(
                new LedgerEntry(REVENUE, 100, LedgerEntryType.CREDIT),
                new LedgerEntry(REVENUE, 50, LedgerEntryType.CREDIT),
                new LedgerEntry(ITEM_BUYER, 150, LedgerEntryType.DEBIT))))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> Ledger.assertBalanced("tx", List.of(
                new LedgerEntry(REVENUE, 100, LedgerEntryType.CREDIT),
                new LedgerEntry(ITEM_BUYER, 90, LedgerEntryType.DEBIT))))
                .isInstanceOf(UnbalancedLedgerException.class);

        assertThatThrownBy(() -> Ledger.assertBalanced("tx", List.of()))
                .isInstanceOf(UnbalancedLedgerException.class);
    }

    @Test
    void verify_balanced_rejects_a_batch_that_does_not_add_up_to_the_payment_total() {
        // 주문 라인 하나가 빠진 경우: 쌍마다는 맞지만 결제 총액과 다르다.
        List<DoubleLedgerEntry> entries = Ledger.createDoubleLedgerEntry(ACCOUNTS, List.of(new PaymentOrder(1, 200, "order")));

        assertThatThrownBy(() -> Ledger.verifyBalanced(entries, 500))
                .isInstanceOf(UnbalancedLedgerException.class)
                .hasMessageContaining("결제 총액");
    }

    @Test
    void verify_balanced_rejects_the_same_transaction_twice_in_a_batch() {
        List<DoubleLedgerEntry> entries = Ledger.createDoubleLedgerEntry(ACCOUNTS, List.of(
                new PaymentOrder(1, 200, "order"), new PaymentOrder(1, 200, "order")));

        assertThatThrownBy(() -> Ledger.verifyBalanced(entries, 400))
                .isInstanceOf(UnbalancedLedgerException.class);
    }

    @Test
    void verify_balanced_rejects_an_empty_batch() {
        assertThatThrownBy(() -> Ledger.verifyBalanced(List.of(), 0))
                .isInstanceOf(UnbalancedLedgerException.class);
    }

    private static LedgerTransaction transaction(long referenceId) {
        return new LedgerTransaction(ReferenceType.PAYMENT_ORDER, referenceId, "order");
    }
}
