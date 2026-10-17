package dev.deepdive.paymentsystem.ledger.domain;

/**
 * 한 거래(LedgerTransaction)의 차변·대변 한 쌍.
 */
public record DoubleLedgerEntry(
        LedgerEntry credit,
        LedgerEntry debit,
        LedgerTransaction transaction
) {

    public DoubleLedgerEntry {
        if (credit.amount() != debit.amount()) {
            throw new IllegalArgumentException(
                    "a double ledger entry require that the amounts for both the credit and debit are same."
            );
        }
        // 금액만 같으면 통과하던 예전 검사로는 "대변 두 줄" 같은 쌍도 만들어졌다. 방향·금액·계정까지 확인한다.
        if (credit.type() != LedgerEntryType.CREDIT || debit.type() != LedgerEntryType.DEBIT) {
            throw new IllegalArgumentException(
                    "대변 자리에는 CREDIT, 차변 자리에는 DEBIT 엔트리가 와야 합니다. credit: " + credit.type() + ", debit: " + debit.type());
        }
        if (credit.amount() <= 0) {
            throw new IllegalArgumentException("장부 금액은 0보다 커야 합니다. amount: " + credit.amount());
        }
        if (credit.account().id() == debit.account().id()) {
            throw new IllegalArgumentException("차변과 대변은 서로 다른 계정이어야 합니다. account: " + credit.account().id());
        }
    }
}
