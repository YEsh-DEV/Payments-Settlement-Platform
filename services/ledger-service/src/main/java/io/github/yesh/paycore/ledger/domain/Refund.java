package io.github.yesh.paycore.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class Refund {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "refund_id", nullable = false, unique = true, length = 64)
  private String refundId;

  @Column(name = "payment_id", nullable = false, length = 64)
  private String paymentId;

  @Column(name = "amount_paise", nullable = false)
  private long amountPaise;

  @Column(columnDefinition = "text")
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private RefundStatus status;

  @Column(name = "journal_id")
  private UUID journalId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public Refund() {}

  public Refund(
      String refundId,
      String paymentId,
      long amountPaise,
      String reason,
      RefundStatus status,
      UUID journalId) {
    this.refundId = refundId;
    this.paymentId = paymentId;
    this.amountPaise = amountPaise;
    this.reason = reason;
    this.status = status;
    this.journalId = journalId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getRefundId() {
    return refundId;
  }

  public String getPaymentId() {
    return paymentId;
  }

  public long getAmountPaise() {
    return amountPaise;
  }

  public String getReason() {
    return reason;
  }

  public RefundStatus getStatus() {
    return status;
  }

  public UUID getJournalId() {
    return journalId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
