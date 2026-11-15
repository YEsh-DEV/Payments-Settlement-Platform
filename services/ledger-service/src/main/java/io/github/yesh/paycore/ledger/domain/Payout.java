package io.github.yesh.paycore.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payouts")
public class Payout {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "payout_id", nullable = false, unique = true, length = 64)
  private String payoutId;

  @Column(name = "seller_id", nullable = false, length = 64)
  private String sellerId;

  @Column(name = "amount_paise", nullable = false)
  private long amountPaise;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PayoutStatus status;

  @Column(name = "batch_id", length = 64)
  private String batchId;

  @Column(name = "journal_id")
  private UUID journalId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public Payout() {}

  public Payout(
      String payoutId,
      String sellerId,
      long amountPaise,
      PayoutStatus status,
      String batchId,
      UUID journalId) {
    this.payoutId = payoutId;
    this.sellerId = sellerId;
    this.amountPaise = amountPaise;
    this.status = status;
    this.batchId = batchId;
    this.journalId = journalId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getPayoutId() {
    return payoutId;
  }

  public String getSellerId() {
    return sellerId;
  }

  public long getAmountPaise() {
    return amountPaise;
  }

  public PayoutStatus getStatus() {
    return status;
  }

  public void setStatus(PayoutStatus status) {
    this.status = status;
  }

  public String getBatchId() {
    return batchId;
  }

  public void setBatchId(String batchId) {
    this.batchId = batchId;
  }

  public UUID getJournalId() {
    return journalId;
  }

  public void setJournalId(UUID journalId) {
    this.journalId = journalId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
