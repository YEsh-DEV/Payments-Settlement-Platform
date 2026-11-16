package io.github.yesh.paycore.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 64)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AccountType type;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Column(name = "balance_paise", nullable = false)
  private long balancePaise = 0L;

  @Column(name = "locked_balance_paise", nullable = false)
  private long lockedBalancePaise = 0L;

  @Column(name = "is_hot", nullable = false)
  private boolean isHot = false;

  @Column(name = "stripe_count", nullable = false)
  private int stripeCount = 1;

  @Version
  @Column(nullable = false)
  private long version = 0L;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public Account() {}

  public Account(String code, AccountType type) {
    this(code, type, false, 1);
  }

  public Account(String code, AccountType type, boolean isHot, int stripeCount) {
    this.code = code;
    this.type = type;
    this.currency = "INR";
    this.isHot = isHot;
    this.stripeCount = Math.max(1, stripeCount);
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public AccountType getType() {
    return type;
  }

  public String getCurrency() {
    return currency;
  }

  public long getBalancePaise() {
    return balancePaise;
  }

  public void setBalancePaise(long balancePaise) {
    this.balancePaise = balancePaise;
  }

  public long getLockedBalancePaise() {
    return lockedBalancePaise;
  }

  public void setLockedBalancePaise(long lockedBalancePaise) {
    this.lockedBalancePaise = lockedBalancePaise;
  }

  public long getAvailableBalancePaise() {
    return balancePaise - lockedBalancePaise;
  }

  public boolean isHot() {
    return isHot;
  }

  public void setHot(boolean hot) {
    isHot = hot;
  }

  public int getStripeCount() {
    return stripeCount;
  }

  public void setStripeCount(int stripeCount) {
    this.stripeCount = stripeCount;
  }

  public long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
