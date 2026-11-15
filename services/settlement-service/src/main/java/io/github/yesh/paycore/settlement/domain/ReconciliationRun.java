package io.github.yesh.paycore.settlement.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "run_id", nullable = false, unique = true, length = 64)
  private String runId;

  @Column(name = "batch_id", length = 64)
  private String batchId;

  @Column(name = "file_id")
  private UUID fileId;

  @Column(name = "from_date")
  private LocalDate fromDate;

  @Column(name = "to_date")
  private LocalDate toDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ReconciliationStatus status = ReconciliationStatus.IN_PROGRESS;

  @Column(name = "matched_count", nullable = false)
  private int matchedCount = 0;

  @Column(name = "discrepancy_count", nullable = false)
  private int discrepancyCount = 0;

  @Column(name = "unmatched_count", nullable = false)
  private int unmatchedCount = 0;

  @Column(name = "total_variance_paise", nullable = false)
  private long totalVariancePaise = 0L;

  @Column(name = "precision_pct")
  private Double precisionPct = 0.0;

  @Column(name = "recall_pct")
  private Double recallPct = 0.0;

  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt = Instant.now();

  @Column(name = "completed_at")
  private Instant completedAt;

  public ReconciliationRun() {}

  public ReconciliationRun(String runId, String batchId, UUID fileId) {
    this.runId = runId;
    this.batchId = batchId;
    this.fileId = fileId;
    this.status = ReconciliationStatus.IN_PROGRESS;
    this.startedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getRunId() {
    return runId;
  }

  public String getBatchId() {
    return batchId;
  }

  public void setBatchId(String batchId) {
    this.batchId = batchId;
  }

  public UUID getFileId() {
    return fileId;
  }

  public void setFileId(UUID fileId) {
    this.fileId = fileId;
  }

  public LocalDate getFromDate() {
    return fromDate;
  }

  public void setFromDate(LocalDate fromDate) {
    this.fromDate = fromDate;
  }

  public LocalDate getToDate() {
    return toDate;
  }

  public void setToDate(LocalDate toDate) {
    this.toDate = toDate;
  }

  public ReconciliationStatus getStatus() {
    return status;
  }

  public void setStatus(ReconciliationStatus status) {
    this.status = status;
  }

  public int getMatchedCount() {
    return matchedCount;
  }

  public void setMatchedCount(int matchedCount) {
    this.matchedCount = matchedCount;
  }

  public int getDiscrepancyCount() {
    return discrepancyCount;
  }

  public void setDiscrepancyCount(int discrepancyCount) {
    this.discrepancyCount = discrepancyCount;
  }

  public int getUnmatchedCount() {
    return unmatchedCount;
  }

  public void setUnmatchedCount(int unmatchedCount) {
    this.unmatchedCount = unmatchedCount;
  }

  public long getTotalVariancePaise() {
    return totalVariancePaise;
  }

  public void setTotalVariancePaise(long totalVariancePaise) {
    this.totalVariancePaise = totalVariancePaise;
  }

  public Double getPrecisionPct() {
    return precisionPct;
  }

  public void setPrecisionPct(Double precisionPct) {
    this.precisionPct = precisionPct;
  }

  public Double getRecallPct() {
    return recallPct;
  }

  public void setRecallPct(Double recallPct) {
    this.recallPct = recallPct;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }
}
