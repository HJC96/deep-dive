package dev.deepdive.paymentsystem.ledger.application.port.out;

import dev.deepdive.paymentsystem.ledger.domain.DoubleAccountsForLedger;
import dev.deepdive.paymentsystem.ledger.domain.FinanceType;

public interface LoadAccountPort {

    DoubleAccountsForLedger getDoubleAccountsForLedger(FinanceType financeType);
}
