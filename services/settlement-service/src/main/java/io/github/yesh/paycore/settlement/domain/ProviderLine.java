package io.github.yesh.paycore.settlement.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "provider_lines")
public class ProviderLine {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "file_id", nullable = false)
  private UUID fileId;

  @Column(name = "line_number", nullable = false)
  private int lineNumber;

  @Column(name = "provider_batch_id", length = 64)
  private String providerBatchId;

  @Column(name = "provider_txn_id", nullable = false, length = 128)
  private String providerTxnId;

  @Column(name = "txn_type", nullable = false, length = 32)
  private String txnType = "CAPTURE";

  @Column(name = "merchant_ref", length = 128)
  private String merchantRef;

  @Column(name = "internal_reference", length = 128)
  private String internalReference;

  @Column(name = "amount_paise", nullable = false)
  private long amountPaise;

  @Column(name = "fee_paise", nullable = false)
  private long feePaise = 0L;

  @Column(name = "net_paise", nullable = false)
  private long netPaise = 0L;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency = "INR";

  @Column(name = "txn_time", nullable = false)
  private Instant txnTime = Instant.now();

  @Column(name = "settle_date", nullable = false)
  private LocalDate settleDate = LocalDate.now();

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ProviderLineStatus status = ProviderLineStatus.INGESTED;

  @Column(name = "raw_data", columnDefinition = "text")
  private String rawData;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public ProviderLine() {}

  public ProviderLine(
      UUID fileId,
      int lineNumber,
      String providerBatchId,
      String providerTxnId,
      String txnType,
      String merchantRef,
      String internalReference,
      long amountPaise,
      long feePaise,
      long netPaise,
      String currency,
      Instant txnTime,
      LocalDate settleDate,
      String rawData) {
    this.fileId = fileId;
    this.lineNumber = lineNumber;
    this.providerBatchId = providerBatchId;
    this.providerTxnId = providerTxnId;
    this.txnType = txnType != null ? txnType : "CAPTURE";
    this.merchantRef = merchantRef;
    this.internalReference = internalReference;
    this.amountPaise = amountPaise;
    this.feePaise = feePaise;
    this.netPaise = netPaise;
    this.currency = currency != null ? currency : "INR";
    this.txnTime = txnTime != null ? txnTime : Instant.now();
    this.settleDate = settleDate != null ? settleDate : LocalDate.now();
    this.status = ProviderLineStatus.INGESTED;
    this.rawData = rawData;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public UUID getFileId() {
    return fileId;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  public String getProviderBatchId() {
    return providerBatchId;
  }

  public String getProviderTxnId() {
    return providerTxnId;
  }

  public String getTxnType() {
    return txnType;
  }

  public String getMerchantRef() {
    return merchantRef;
  }

  public String getInternalReference() {
    return internalReference;
  }

  public void setInternalReference(String internalReference) {
    this.internalReference = internalReference;
  }

  public long getAmountPaise() {
    return amountPaise;
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

  public Instant getTxnTime() {
    return txnTime;
  }

  public LocalDate getSettleDate() {
    return settleDate;
  }

  public ProviderLineStatus getStatus() {
    return status;
  }

  public void setStatus(ProviderLineStatus status) {
    this.status = status;
  }

  public String getRawData() {
    return rawData;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
