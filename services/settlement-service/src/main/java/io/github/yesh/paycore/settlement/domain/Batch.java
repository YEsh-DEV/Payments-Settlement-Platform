package io.github.yesh.paycore.settlement.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "batches")
public class Batch {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "batch_id", nullable = false, unique = true, length = 64)
  private String batchId;

  @Column(name = "cutoff_window", nullable = false, length = 64)
  private String cutoffWindow;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private BatchStatus status = BatchStatus.OPEN;

  @Column(name = "total_count", nullable = false)
  private int totalCount = 0;

  @Column(name = "total_gross_paise", nullable = false)
  private long totalGrossPaise = 0L;

  @Column(name = "total_fee_paise", nullable = false)
  private long totalFeePaise = 0L;

  @Column(name = "total_net_paise", nullable = false)
  private long totalNetPaise = 0L;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "closed_at")
  private Instant closedAt;

  public Batch() {}

  public Batch(String batchId, String cutoffWindow) {
    this.batchId = batchId;
    this.cutoffWindow = cutoffWindow;
    this.status = BatchStatus.OPEN;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getBatchId() {
    return batchId;
  }

  public String getCutoffWindow() {
    return cutoffWindow;
  }

  public BatchStatus getStatus() {
    return status;
  }

  public void setStatus(BatchStatus status) {
    this.status = status;
  }

  public int getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(int totalCount) {
    this.totalCount = totalCount;
  }

  public long getTotalGrossPaise() {
    return totalGrossPaise;
  }

  public void setTotalGrossPaise(long totalGrossPaise) {
    this.totalGrossPaise = totalGrossPaise;
  }

  public long getTotalFeePaise() {
    return totalFeePaise;
  }

  public void setTotalFeePaise(long totalFeePaise) {
    this.totalFeePaise = totalFeePaise;
  }

  public long getTotalNetPaise() {
    return totalNetPaise;
  }

  public void setTotalNetPaise(long totalNetPaise) {
    this.totalNetPaise = totalNetPaise;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public void setClosedAt(Instant closedAt) {
    this.closedAt = closedAt;
  }
}
