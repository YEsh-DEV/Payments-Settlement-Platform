package io.github.yesh.paycore.ledger.dto;

import io.github.yesh.paycore.ledger.domain.EntryDirection;
import io.github.yesh.paycore.ledger.domain.LedgerEntry;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryDto(
    Long id,
    UUID journalId,
    UUID accountId,
    EntryDirection direction,
    long amountPaise,
    long runningBalancePaise,
    Instant createdAt) {

  public static LedgerEntryDto from(LedgerEntry entry) {
    return new LedgerEntryDto(
        entry.getId(),
        entry.getJournalId(),
        entry.getAccountId(),
        entry.getDirection(),
        entry.getAmountPaise(),
        entry.getRunningBalancePaise(),
        entry.getCreatedAt());
  }
}
