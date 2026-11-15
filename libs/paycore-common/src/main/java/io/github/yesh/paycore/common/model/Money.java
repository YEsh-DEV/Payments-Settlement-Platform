package io.github.yesh.paycore.common.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable monetary representation in minor units (paise). Floating-point arithmetic is strictly
 * prohibited in PayCore.
 *
 * @param amount amount in minor units (1 INR = 100 paise)
 * @param currency ISO-4217 3-letter currency code (v1 supports exclusively "INR")
 */
public record Money(long amount, String currency) implements Comparable<Money>, Serializable {

  public static final String DEFAULT_CURRENCY = "INR";
  public static final Money ZERO = new Money(0L, DEFAULT_CURRENCY);

  public Money {
    Objects.requireNonNull(currency, "Currency must not be null");
    if (!DEFAULT_CURRENCY.equalsIgnoreCase(currency)) {
      throw new IllegalArgumentException(
          "Unsupported currency: " + currency + ". PayCore v1 exclusively supports INR.");
    }
    currency = currency.toUpperCase();
  }

  public static Money ofPaise(long paise) {
    return new Money(paise, DEFAULT_CURRENCY);
  }

  public static Money zero() {
    return ZERO;
  }

  public long paise() {
    return this.amount;
  }

  public static Money ofRupees(long rupees) {
    return new Money(Math.multiplyExact(rupees, 100L), DEFAULT_CURRENCY);
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.addExact(this.amount, other.amount), this.currency);
  }

  public Money minus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.subtractExact(this.amount, other.amount), this.currency);
  }

  public Money multiply(long factor) {
    return new Money(Math.multiplyExact(this.amount, factor), this.currency);
  }

  /** Calculates fee or share in basis points using ROUND_HALF_UP arithmetic. bps: 100 bps = 1% */
  public Money percentageBps(int bps) {
    if (bps < 0 || bps > 10000) {
      throw new IllegalArgumentException("Basis points must be between 0 and 10000: " + bps);
    }
    long numerator = Math.multiplyExact(this.amount, (long) bps);
    long result = (numerator + 5000L) / 10000L;
    return new Money(result, this.currency);
  }

  public Money calculateFeeBps(int bps) {
    return percentageBps(bps);
  }

  public boolean isPositive() {
    return this.amount > 0;
  }

  public boolean isZero() {
    return this.amount == 0;
  }

  public boolean isNegative() {
    return this.amount < 0;
  }

  public boolean isGreaterThanOrEqual(Money other) {
    requireSameCurrency(other);
    return this.amount >= other.amount;
  }

  public boolean isLessThan(Money other) {
    requireSameCurrency(other);
    return this.amount < other.amount;
  }

  private void requireSameCurrency(Money other) {
    Objects.requireNonNull(other, "Compared money must not be null");
    if (!this.currency.equals(other.currency)) {
      throw new IllegalArgumentException(
          "Currency mismatch: " + this.currency + " vs " + other.currency);
    }
  }

  @Override
  public int compareTo(Money other) {
    requireSameCurrency(other);
    return Long.compare(this.amount, other.amount);
  }

  @Override
  public String toString() {
    return String.format("%s %d.%02d", currency, amount / 100, Math.abs(amount % 100));
  }
}
