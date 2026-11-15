package io.github.yesh.paycore.settlement.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bank_deposits")
public class BankDeposit {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "bank_deposit_id", nullable = false, unique = true, length = 64)
  private String bankDepositId;

  @Column(name = "value_date", nullable = false)
  private LocalDate valueDate;

  @Column(name = "amount_paise", nullable = false)
  private long amountPaise;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Column(columnDefinition = "text")
  private String reference;

  @Column(nullable = false, length = 32)
  private String status = "UNMATCHED";

  @Column(name = "matched_batch_id", length = 64)
  private String matchedBatchId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public BankDeposit() {}

  public BankDeposit(
      String bankDepositId,
      LocalDate valueDate,
      long amountPaise,
      String currency,
      String reference) {
    this.bankDepositId = bankDepositId;
    this.valueDate = valueDate;
    this.amountPaise = amountPaise;
    this.currency = currency != null ? currency : "INR";
    this.reference = reference;
    this.status = "UNMATCHED";
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getBankDepositId() {
    return bankDepositId;
  }

  public LocalDate getValueDate() {
    return valueDate;
  }

  public long getAmountPaise() {
    return amountPaise;
  }

  public String getCurrency() {
    return currency;
  }

  public String getReference() {
    return reference;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getMatchedBatchId() {
    return matchedBatchId;
  }

  public void setMatchedBatchId(String matchedBatchId) {
    this.matchedBatchId = matchedBatchId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
