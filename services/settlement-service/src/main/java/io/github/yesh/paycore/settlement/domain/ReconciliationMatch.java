package io.github.yesh.paycore.settlement.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reconciliation_matches")
public class ReconciliationMatch {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "run_id", nullable = false, length = 64)
  private String runId;

  @Column(name = "record_id", nullable = false, length = 64)
  private String recordId;

  @Column(name = "provider_line_id")
  private Long providerLineId;

  @Column(name = "bank_deposit_id", length = 64)
  private String bankDepositId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ReconciliationStage stage;

  @Column(nullable = false, length = 64)
  private String rule;

  @Column(nullable = false)
  private double confidence;

  @Column(name = "evidence_json", columnDefinition = "text")
  private String evidenceJson;

  @Column(name = "matched_amount_paise", nullable = false)
  private long matchedAmountPaise;

  @Column(name = "variance_paise", nullable = false)
  private long variancePaise = 0L;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public ReconciliationMatch() {}

  public ReconciliationMatch(
      String runId,
      String recordId,
      Long providerLineId,
      String bankDepositId,
      ReconciliationStage stage,
      String rule,
      double confidence,
      String evidenceJson,
      long matchedAmountPaise,
      long variancePaise) {
    this.runId = runId;
    this.recordId = recordId;
    this.providerLineId = providerLineId;
    this.bankDepositId = bankDepositId;
    this.stage = stage;
    this.rule = rule;
    this.confidence = confidence;
    this.evidenceJson = evidenceJson;
    this.matchedAmountPaise = matchedAmountPaise;
    this.variancePaise = variancePaise;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getRunId() {
    return runId;
  }

  public String getRecordId() {
    return recordId;
  }

  public Long getProviderLineId() {
    return providerLineId;
  }

  public String getBankDepositId() {
    return bankDepositId;
  }

  public ReconciliationStage getStage() {
    return stage;
  }

  public String getRule() {
    return rule;
  }

  public double getConfidence() {
    return confidence;
  }

  public String getEvidenceJson() {
    return evidenceJson;
  }

  public long getMatchedAmountPaise() {
    return matchedAmountPaise;
  }

  public long getVariancePaise() {
    return variancePaise;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
