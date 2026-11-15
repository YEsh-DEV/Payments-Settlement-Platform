package io.github.yesh.paycore.ledger.repository;

import io.github.yesh.paycore.ledger.domain.LedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

  List<LedgerEntry> findByAccountIdAndIdGreaterThanOrderByIdAsc(
      UUID accountId, Long lastSeenId, Pageable pageable);

  List<LedgerEntry> findByAccountIdOrderByIdAsc(UUID accountId, Pageable pageable);

  List<LedgerEntry> findByJournalId(UUID journalId);
}
