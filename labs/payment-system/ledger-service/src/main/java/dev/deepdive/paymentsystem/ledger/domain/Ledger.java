package dev.deepdive.paymentsystem.ledger.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Ledger {

    public static List<DoubleLedgerEntry> createDoubleLedgerEntry(
            DoubleAccountsForLedger doubleAccountsForLedger,
            List<? extends Item> items
    ) {
        return items.stream()
                .map(item -> new DoubleLedgerEntry(
                        new LedgerEntry(
                                doubleAccountsForLedger.to(),
                                item.amount(),
                                LedgerEntryType.CREDIT
                        ),
                        new LedgerEntry(
                                doubleAccountsForLedger.from(),
                                item.amount(),
                                LedgerEntryType.DEBIT
                        ),
                        new LedgerTransaction(
                                item.type(),
                                item.id(),
                                item.orderId()
                        )
                ))
                .toList();
    }

    /**
     * 저장 직전 불변식 검사.
     * <ol>
     *     <li>거래(LedgerTransaction)마다 DEBIT 합 == CREDIT 합 (엔트리의 방향 기준으로 다시 더한다)</li>
     *     <li>한 배치에 같은 거래가 두 번 없다</li>
     *     <li>이번 배치의 CREDIT 합 == 결제 총액 (주문 라인이 빠지거나 중복되면 걸린다)</li>
     * </ol>
     * 하나라도 어긋나면 {@link UnbalancedLedgerException} 을 던지고 아무것도 저장하지 않는다.
     */
    public static void verifyBalanced(List<DoubleLedgerEntry> doubleLedgerEntries, long expectedTotalAmount) {
        if (doubleLedgerEntries.isEmpty()) {
            throw new UnbalancedLedgerException("기록할 장부 엔트리가 없습니다.");
        }

        Map<LedgerTransaction, List<LedgerEntry>> entriesByTransaction = new LinkedHashMap<>();
        for (DoubleLedgerEntry entry : doubleLedgerEntries) {
            entriesByTransaction.computeIfAbsent(entry.transaction(), it -> new ArrayList<>())
                    .addAll(List.of(entry.credit(), entry.debit()));
        }
        if (entriesByTransaction.size() != doubleLedgerEntries.size()) {
            // 같은 주문 라인이 한 배치에 두 번 들어오면 UNIQUE 위반이 "이미 처리됨"으로 오인될 수 있다. 미리 막는다.
            throw new UnbalancedLedgerException("한 배치에 같은 장부 거래가 중복돼 있습니다.");
        }
        entriesByTransaction.forEach((transaction, entries) -> assertBalanced(transaction, entries));

        long totalCredit = doubleLedgerEntries.stream()
                .flatMap(entry -> Stream.of(entry.credit(), entry.debit()))
                .filter(entry -> entry.type() == LedgerEntryType.CREDIT)
                .mapToLong(LedgerEntry::amount)
                .sum();
        if (totalCredit != expectedTotalAmount) {
            throw new UnbalancedLedgerException(
                    "장부 대변 합(" + totalCredit + ")이 결제 총액(" + expectedTotalAmount + ")과 다릅니다.");
        }
    }

    /**
     * 한 거래에 속한 엔트리들의 DEBIT 합과 CREDIT 합이 같은지 확인한다.
     */
    public static void assertBalanced(Object transaction, Collection<LedgerEntry> entries) {
        long debit = sum(entries, LedgerEntryType.DEBIT);
        long credit = sum(entries, LedgerEntryType.CREDIT);
        if (entries.isEmpty() || debit != credit) {
            throw new UnbalancedLedgerException(
                    "거래 " + transaction + " 의 차변 합(" + debit + ")과 대변 합(" + credit + ")이 다릅니다.");
        }
    }

    private static long sum(Collection<LedgerEntry> entries, LedgerEntryType type) {
        return entries.stream()
                .filter(entry -> entry.type() == type)
                .mapToLong(LedgerEntry::amount)
                .sum();
    }
}
