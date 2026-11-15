package io.github.yesh.paycore.ledger.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yesh.paycore.common.model.Money;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

class JournalBalancePropertyTest {

  @Property(tries = 1000)
  @Label("Invariant I1: Double-Entry Balance holds for arbitrary payment and fee splits")
  void doubleEntryBalanceHoldsForArbitrarySplits(
      @ForAll @LongRange(min = 1, max = 10_000_000_000L) long grossPaise,
      @ForAll @IntRange(min = 0, max = 10000) int feeBps) {

    Money gross = Money.ofPaise(grossPaise);
    Money fee = gross.calculateFeeBps(feeBps);
    Money net = gross.minus(fee);

    // Invariant I1: Sum of debits must identically equal sum of credits
    Money totalDebits = gross; // DR User Funds
    Money totalCredits = net.plus(fee); // CR Merchant + CR Fee Revenue

    assertThat(totalCredits).isEqualTo(totalDebits);
    assertThat(net.paise() + fee.paise()).isEqualTo(gross.paise());
    assertThat(net.paise()).isGreaterThanOrEqualTo(0L);
    assertThat(fee.paise()).isGreaterThanOrEqualTo(0L);
  }

  @Property(tries = 500)
  @Label("Invariant I1: Multi-seller composite payment split balances exactly")
  void multiSellerCompositeJournalBalances(
      @ForAll @LongRange(min = 100, max = 1_000_000_000L) long item1Paise,
      @ForAll @LongRange(min = 100, max = 1_000_000_000L) long item2Paise,
      @ForAll @IntRange(min = 50, max = 500) int seller1Bps,
      @ForAll @IntRange(min = 50, max = 500) int seller2Bps) {

    Money m1 = Money.ofPaise(item1Paise);
    Money m2 = Money.ofPaise(item2Paise);
    Money totalGross = m1.plus(m2);

    Money fee1 = m1.calculateFeeBps(seller1Bps);
    Money net1 = m1.minus(fee1);

    Money fee2 = m2.calculateFeeBps(seller2Bps);
    Money net2 = m2.minus(fee2);

    Money totalCredits = net1.plus(fee1).plus(net2).plus(fee2);
    assertThat(totalCredits).isEqualTo(totalGross);
  }
}
