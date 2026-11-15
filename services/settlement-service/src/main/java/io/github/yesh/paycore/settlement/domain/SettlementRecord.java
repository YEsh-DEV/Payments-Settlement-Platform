package io.github.yesh.paycore.settlement.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_records")
public class SettlementRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "record_id", nullable = false, unique = true, length = 64)
  private String recordId;

  @Column(name = "batch_id", length = 64)
  private String batchId;

  @Column(name = "payment_id", nullable = false, length = 64)
  private String paymentId;

  @Column(name = "seller_id", nullable = false, length = 64)
  private String sellerId;

  @Column(name = "provider_txn_id", length = 128)
  private String providerTxnId;

  @Column(name = "order_id", length = 64)
  private String orderId;

  @Column(name = "kind", nullable = false, length = 32)
  private String kind = "CAPTURE";

  @Column(name = "gross_paise", nullable = false)
  private long grossPaise;

  @Column(name = "fee_paise", nullable = false)
  private long feePaise;

  @Column(name = "net_paise", nullable = false)
  private long netPaise;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private SettlementRecordStatus status = SettlementRecordStatus.PENDING;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt = Instant.now();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public SettlementRecord() {}

  public SettlementRecord(
      String recordId,
      String paymentId,
      String sellerId,
      String providerTxnId,
      String orderId,
      String kind,
      long grossPaise,
      long feePaise,
      long netPaise,
      Instant occurredAt) {
    this.recordId = recordId;
    this.paymentId = paymentId;
    this.sellerId = sellerId;
    this.providerTxnId = providerTxnId;
    this.orderId = orderId;
    this.kind = kind != null ? kind : "CAPTURE";
    this.grossPaise = grossPaise;
    this.feePaise = feePaise;
    this.netPaise = netPaise;
    this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
    this.status = SettlementRecordStatus.PENDING;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getRecordId() {
    return recordId;
  }

  public String getBatchId() {
    return batchId;
  }

  public void setBatchId(String batchId) {
    this.batchId = batchId;
  }

  public String getPaymentId() {
    return paymentId;
  }

  public String getSellerId() {
    return sellerId;
  }

  public String getProviderTxnId() {
    return providerTxnId;
  }

  public void setProviderTxnId(String providerTxnId) {
    this.providerTxnId = providerTxnId;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
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

  public SettlementRecordStatus getStatus() {
    return status;
  }

  public void setStatus(SettlementRecordStatus status) {
    this.status = status;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
