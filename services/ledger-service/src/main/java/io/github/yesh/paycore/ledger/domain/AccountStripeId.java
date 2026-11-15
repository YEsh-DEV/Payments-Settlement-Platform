package io.github.yesh.paycore.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AccountStripeId implements Serializable {

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "stripe_index", nullable = false)
  private int stripeIndex;

  public AccountStripeId() {}

  public AccountStripeId(UUID accountId, int stripeIndex) {
    this.accountId = accountId;
    this.stripeIndex = stripeIndex;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public int getStripeIndex() {
    return stripeIndex;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AccountStripeId that = (AccountStripeId) o;
    return stripeIndex == that.stripeIndex && Objects.equals(accountId, that.accountId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accountId, stripeIndex);
  }
}
