package io.github.yesh.paycore.settlement.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_settlement_files")
public class ProviderSettlementFile {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 128)
  private String filename;

  @Column(name = "file_hash", nullable = false, unique = true, length = 64)
  private String fileHash;

  @Column(nullable = false, length = 64)
  private String provider;

  @Column(name = "total_records", nullable = false)
  private int totalRecords;

  @Column(name = "total_amount_paise", nullable = false)
  private long totalAmountPaise;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ProviderFileStatus status = ProviderFileStatus.INGESTED;

  @Column(name = "ingested_at", nullable = false, updatable = false)
  private Instant ingestedAt = Instant.now();

  public ProviderSettlementFile() {}

  public ProviderSettlementFile(
      String filename, String fileHash, String provider, int totalRecords, long totalAmountPaise) {
    this.filename = filename;
    this.fileHash = fileHash;
    this.provider = provider;
    this.totalRecords = totalRecords;
    this.totalAmountPaise = totalAmountPaise;
    this.status = ProviderFileStatus.INGESTED;
    this.ingestedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getFilename() {
    return filename;
  }

  public String getFileHash() {
    return fileHash;
  }

  public String getProvider() {
    return provider;
  }

  public int getTotalRecords() {
    return totalRecords;
  }

  public long getTotalAmountPaise() {
    return totalAmountPaise;
  }

  public ProviderFileStatus getStatus() {
    return status;
  }

  public void setStatus(ProviderFileStatus status) {
    this.status = status;
  }

  public void setFileHash(String fileHash) {
    this.fileHash = fileHash;
  }

  public void setTotalRecords(int totalRecords) {
    this.totalRecords = totalRecords;
  }

  public void setTotalAmountPaise(long totalAmountPaise) {
    this.totalAmountPaise = totalAmountPaise;
  }

  public Instant getIngestedAt() {
    return ingestedAt;
  }
}
