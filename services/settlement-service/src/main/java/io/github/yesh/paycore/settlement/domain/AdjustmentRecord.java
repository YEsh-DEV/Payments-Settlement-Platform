package io.github.yesh.paycore.settlement.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "adjustments")
public class AdjustmentRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "adjustment_id", nullable = false, unique = true, length = 64)
  private String adjustmentId;

  @Column(name = "case_id", nullable = false, length = 64)
  private String caseId;

  @Column(nullable = false, length = 32)
  private String template;

  @Column(name = "debit_account", nullable = false, length = 64)
  private String debitAccount;

  @Column(name = "credit_account", nullable = false, length = 64)
  private String creditAccount;

  @Column(name = "amount_paise", nullable = false)
  private long amountPaise;

  @Column(nullable = false, length = 32)
  private String status = "PROPOSED";

  @Column(name = "proposed_by", nullable = false, length = 64)
  private String proposedBy;

  @Column(name = "approved_by", length = 64)
  private String approvedBy;

  @Column(name = "ledger_journal_id")
  private UUID ledgerJournalId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public AdjustmentRecord() {}

  public AdjustmentRecord(
      String adjustmentId,
      String caseId,
      String template,
      String debitAccount,
      String creditAccount,
      long amountPaise,
      String proposedBy) {
    this.adjustmentId = adjustmentId;
    this.caseId = caseId;
    this.template = template;
    this.debitAccount = debitAccount;
    this.creditAccount = creditAccount;
    this.amountPaise = amountPaise;
    this.proposedBy = proposedBy;
    this.status = "PROPOSED";
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getAdjustmentId() {
    return adjustmentId;
  }

  public String getCaseId() {
    return caseId;
  }

  public String getTemplate() {
    return template;
  }

  public String getDebitAccount() {
    return debitAccount;
  }

  public String getCreditAccount() {
    return creditAccount;
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

  public String getProposedBy() {
    return proposedBy;
  }

  public String getApprovedBy() {
    return approvedBy;
  }

  public void setApprovedBy(String approvedBy) {
    this.approvedBy = approvedBy;
    this.updatedAt = Instant.now();
  }

  public UUID getLedgerJournalId() {
    return ledgerJournalId;
  }

  public void setLedgerJournalId(UUID ledgerJournalId) {
    this.ledgerJournalId = ledgerJournalId;
    this.updatedAt = Instant.now();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
