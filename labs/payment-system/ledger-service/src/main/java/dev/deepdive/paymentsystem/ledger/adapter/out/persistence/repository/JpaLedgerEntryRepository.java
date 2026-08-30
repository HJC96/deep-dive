package dev.deepdive.paymentsystem.ledger.adapter.out.persistence.repository;

import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity.JpaLedgerEntryEntity;
import dev.deepdive.paymentsystem.ledger.adapter.out.persistence.entity.JpaLedgerEntryMapper;
import dev.deepdive.paymentsystem.ledger.domain.DoubleLedgerEntry;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JpaLedgerEntryRepository implements LedgerEntryRepository {

    private final SpringDataJpaLedgerEntryRepository springDataJpaLedgerEntryRepository;
    private final JpaLedgerEntryMapper jpaLedgerEntryMapper;

    public JpaLedgerEntryRepository(
            SpringDataJpaLedgerEntryRepository springDataJpaLedgerEntryRepository,
            JpaLedgerEntryMapper jpaLedgerEntryMapper
    ) {
        this.springDataJpaLedgerEntryRepository = springDataJpaLedgerEntryRepository;
        this.jpaLedgerEntryMapper = jpaLedgerEntryMapper;
    }

    @Override
    public void save(List<DoubleLedgerEntry> doubleLedgerEntries) {
        springDataJpaLedgerEntryRepository.saveAll(
                doubleLedgerEntries.stream()
                        .flatMap(entry -> jpaLedgerEntryMapper.mapToJpaEntity(entry).stream())
                        .toList()
        );
    }
}
