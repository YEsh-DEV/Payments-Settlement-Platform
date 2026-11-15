package io.github.yesh.paycore.ledger.repository;

import io.github.yesh.paycore.ledger.domain.EntryDirection;
import io.github.yesh.paycore.ledger.domain.LedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

  List<LedgerEntry> findByAccountIdAndIdGreaterThanOrderByIdAsc(
      UUID accountId, Long lastSeenId, Pageable pageable);

  List<LedgerEntry> findByAccountIdOrderByIdAsc(UUID accountId, Pageable pageable);

  List<LedgerEntry> findByJournalId(UUID journalId);

  @Query("SELECT COALESCE(SUM(e.amountPaise), 0) FROM LedgerEntry e WHERE e.direction = :direction")
  Long sumTotalByDirection(@Param("direction") EntryDirection direction);

  @Query(
      "SELECT COALESCE(SUM(e.amountPaise), 0) FROM LedgerEntry e WHERE e.accountId = :accountId AND e.direction = :direction")
  Long sumAmountByAccountIdAndDirection(
      @Param("accountId") UUID accountId, @Param("direction") EntryDirection direction);

  @Query(
      "SELECT COALESCE(SUM(e.amountPaise), 0) FROM LedgerEntry e WHERE e.accountId = :accountId AND e.direction = :direction AND e.id > :afterEntryId")
  Long sumAmountByAccountIdAndDirectionAndIdGreaterThan(
      @Param("accountId") UUID accountId,
      @Param("direction") EntryDirection direction,
      @Param("afterEntryId") long afterEntryId);
}
