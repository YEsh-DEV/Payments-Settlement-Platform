package io.github.yesh.paycore.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_snapshots")
public class AccountSnapshot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "as_of_entry_id", nullable = false)
  private long asOfEntryId;

  @Column(name = "balance_paise", nullable = false)
  private long balancePaise;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public AccountSnapshot() {}

  public AccountSnapshot(UUID accountId, long asOfEntryId, long balancePaise) {
    this.accountId = accountId;
    this.asOfEntryId = asOfEntryId;
    this.balancePaise = balancePaise;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public long getAsOfEntryId() {
    return asOfEntryId;
  }

  public long getBalancePaise() {
    return balancePaise;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
