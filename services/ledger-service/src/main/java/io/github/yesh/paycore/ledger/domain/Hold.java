package io.github.yesh.paycore.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "holds")
public class Hold {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "hold_id", nullable = false, unique = true, length = 64)
  private String holdId;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "amount_paise", nullable = false)
  private long amountPaise;

  @Column(columnDefinition = "text")
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private HoldStatus status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public Hold() {}

  public Hold(
      String holdId,
      UUID accountId,
      long amountPaise,
      String reason,
      HoldStatus status,
      Instant expiresAt) {
    this.holdId = holdId;
    this.accountId = accountId;
    this.amountPaise = amountPaise;
    this.reason = reason;
    this.status = status;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getHoldId() {
    return holdId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public long getAmountPaise() {
    return amountPaise;
  }

  public String getReason() {
    return reason;
  }

  public HoldStatus getStatus() {
    return status;
  }

  public void setStatus(HoldStatus status) {
    this.status = status;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
