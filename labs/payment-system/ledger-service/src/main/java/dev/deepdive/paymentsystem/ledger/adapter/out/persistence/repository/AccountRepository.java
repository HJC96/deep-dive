package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.ledger.domain.DoubleAccountsForLedger;
import dev.deepdive.paymentsystem.ledger.domain.FinanceType;

public interface AccountRepository {

    DoubleAccountsForLedger getDoubleAccountsForLedger(FinanceType financeType);
}
