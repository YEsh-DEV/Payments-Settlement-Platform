package io.github.yesh.paycore.ledger.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "account_stripes")
public class AccountStripe {

  @EmbeddedId private AccountStripeId id;

  @Column(name = "balance_paise", nullable = false)
  private long balancePaise = 0L;

  public AccountStripe() {}

  public AccountStripe(AccountStripeId id, long balancePaise) {
    this.id = id;
    this.balancePaise = balancePaise;
  }

  public AccountStripeId getId() {
    return id;
  }

  public long getBalancePaise() {
    return balancePaise;
  }

  public void setBalancePaise(long balancePaise) {
    this.balancePaise = balancePaise;
  }
}
