package io.github.yesh.paycore.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "journal_id", nullable = false)
  private UUID journalId;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 2)
  private EntryDirection direction;

  @Column(name = "amount_paise", nullable = false)
  private long amountPaise;

  @Column(name = "running_balance_paise", nullable = false)
  private long runningBalancePaise;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public LedgerEntry() {}

  public LedgerEntry(
      UUID journalId,
      UUID accountId,
      EntryDirection direction,
      long amountPaise,
      long runningBalancePaise) {
    this.journalId = journalId;
    this.accountId = accountId;
    this.direction = direction;
    this.amountPaise = amountPaise;
    this.runningBalancePaise = runningBalancePaise;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public UUID getJournalId() {
    return journalId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public EntryDirection getDirection() {
    return direction;
  }

  public long getAmountPaise() {
    return amountPaise;
  }

  public long getRunningBalancePaise() {
    return runningBalancePaise;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
