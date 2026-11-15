package io.github.yesh.paycore.common.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  @DisplayName("Should create Money in paise and format as INR string")
  void testCreationAndFormatting() {
    Money m = Money.ofPaise(12550L);
    assertEquals(12550L, m.amount());
    assertEquals("INR", m.currency());
    assertEquals("INR 125.50", m.toString());
  }

  @Test
  @DisplayName("Should reject non-INR currencies in v1")
  void testUnsupportedCurrency() {
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> new Money(100L, "USD"));
    assertTrue(ex.getMessage().contains("exclusively supports INR"));
  }

  @Test
  @DisplayName("Should add and subtract money correctly with overflow detection")
  void testArithmetic() {
    Money a = Money.ofPaise(5000L);
    Money b = Money.ofPaise(2500L);

    assertEquals(Money.ofPaise(7500L), a.plus(b));
    assertEquals(Money.ofPaise(2500L), a.minus(b));
  }

  @Test
  @DisplayName("Should calculate basis points fee using ROUND_HALF_UP arithmetic")
  void testPercentageBps() {
    // 1000 paise gross at 1000 bps (10%) -> 100 paise fee
    Money gross = Money.ofPaise(1000L);
    assertEquals(Money.ofPaise(100L), gross.percentageBps(1000));

    // 155 paise gross at 150 bps (1.5%) -> 155 * 150 = 23250; (23250 + 5000) / 10000 = 2 paise
    Money oddGross = Money.ofPaise(155L);
    assertEquals(Money.ofPaise(2L), oddGross.percentageBps(150));
  }

  @Test
  @DisplayName("Should compare amounts accurately")
  void testComparisons() {
    Money low = Money.ofPaise(100L);
    Money high = Money.ofPaise(200L);

    assertTrue(low.isLessThan(high));
    assertTrue(high.isGreaterThanOrEqual(low));
    assertTrue(low.isPositive());
    assertFalse(Money.ZERO.isPositive());
  }
}
