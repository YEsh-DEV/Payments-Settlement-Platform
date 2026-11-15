package io.github.yesh.paycore.settlement.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_payouts")
public class SettlementPayout {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "payout_id", nullable = false, unique = true, length = 64)
  private String payoutId;

  @Column(name = "batch_id", nullable = false, length = 64)
  private String batchId;

  @Column(name = "seller_id", nullable = false, length = 64)
  private String sellerId;

  @Column(name = "amount_paise", nullable = false)
  private long amountPaise;

  @Column(nullable = false, length = 32)
  private String status = "REQUESTED";

  @Column(name = "ledger_payout_id", length = 64)
  private String ledgerPayoutId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public SettlementPayout() {}

  public SettlementPayout(String payoutId, String batchId, String sellerId, long amountPaise) {
    this.payoutId = payoutId;
    this.batchId = batchId;
    this.sellerId = sellerId;
    this.amountPaise = amountPaise;
    this.status = "REQUESTED";
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getPayoutId() {
    return payoutId;
  }

  public String getBatchId() {
    return batchId;
  }

  public String getSellerId() {
    return sellerId;
  }

  public long getAmountPaise() {
    return amountPaise;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
    this.updatedAt = Instant.now();
  }

  public String getLedgerPayoutId() {
    return ledgerPayoutId;
  }

  public void setLedgerPayoutId(String ledgerPayoutId) {
    this.ledgerPayoutId = ledgerPayoutId;
    this.updatedAt = Instant.now();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
