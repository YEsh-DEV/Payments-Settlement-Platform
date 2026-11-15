package io.github.yesh.paycore.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "payment_id", nullable = false, unique = true, length = 64)
  private String paymentId;

  @Column(name = "seller_id", nullable = false, length = 64)
  private String sellerId;

  @Column(name = "gross_paise", nullable = false)
  private long grossPaise;

  @Column(name = "fee_paise", nullable = false)
  private long feePaise;

  @Column(name = "net_paise", nullable = false)
  private long netPaise;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PaymentStatus status;

  @Column(name = "journal_id")
  private UUID journalId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public Payment() {}

  public Payment(
      String paymentId,
      String sellerId,
      long grossPaise,
      long feePaise,
      long netPaise,
      PaymentStatus status,
      UUID journalId) {
    this.paymentId = paymentId;
    this.sellerId = sellerId;
    this.grossPaise = grossPaise;
    this.feePaise = feePaise;
    this.netPaise = netPaise;
    this.currency = "INR";
    this.status = status;
    this.journalId = journalId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getPaymentId() {
    return paymentId;
  }

  public String getSellerId() {
    return sellerId;
  }

  public long getGrossPaise() {
    return grossPaise;
  }

  public long getFeePaise() {
    return feePaise;
  }

  public long getNetPaise() {
    return netPaise;
  }

  public String getCurrency() {
    return currency;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
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
