package dev.deepdive.paymentsystem.ledger.adapter.out.persistence;

import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository.AccountRepository;
import dev.deepdive.paymentsystem.ledger.application.port.out.LoadAccountPort;
import dev.deepdive.paymentsystem.ledger.common.PersistenceAdapter;
import dev.deepdive.paymentsystem.ledger.domain.DoubleAccountsForLedger;
import dev.deepdive.paymentsystem.ledger.domain.FinanceType;

@PersistenceAdapter
public class AccountPersistenceAdapter implements LoadAccountPort {

    private final AccountRepository accountRepository;

    public AccountPersistenceAdapter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public DoubleAccountsForLedger getDoubleAccountsForLedger(FinanceType financeType) {
        return accountRepository.getDoubleAccountsForLedger(financeType);
    }
}
