# ADR-003: Money Representation, Minor Units (Paise), and Lossless Fee Arithmetic

## Status
Accepted

## Context
Marketplace platforms process financial transactions where fees are calculated as basis points (bps) and split across platform revenues and seller payables. In digital ledger accounting, IEEE 754 floating-point arithmetic (`float`, `double`, or unconstrained `BigDecimal`) introduces rounding precision drift (e.g. $0.10 + 0.20 \ne 0.30$), which breaks double-entry balance invariants ($\sum \text{Debits} = \sum \text{Credits}$).

## Decision
1. **Integer Minor Units:** All monetary values in PayCore are represented as `long` in Java and `BIGINT` in PostgreSQL representing minor units (**paise**, where $1\text{ INR} = 100\text{ paise}$).
2. **Fixed Single Currency:** Currency for v1 is fixed to `"INR"`. All write endpoints validate currency code equality.
3. **Lossless Remainder Allocation:**
   $$\text{fee} = \text{round\_half\_up}\left(\frac{\text{gross} \times \text{feeBps}}{10000}\right)$$
   $$\text{sellerShare} = \text{gross} - \text{fee}$$
   By calculating the seller share as the difference between gross and fee, the sum of debits and credits is guaranteed to equal gross paise without losing a single paisa.
4. **Proportional Refund Split:**
   $$\text{sellerPortion} = \text{round\_half\_up}\left(\frac{\text{refundAmount} \times \text{originalSellerShare}}{\text{originalGross}}\right)$$
   $$\text{feePortion} = \text{refundAmount} - \text{sellerPortion}$$
5. **Overflow Prevention:** Arithmetic computations use `Math.addExact` and `Math.multiplyExact` to ensure integer overflow raises an immediate `ArithmeticException` rather than silently wrapping values.

## Consequences
- Guaranteed zero-sum balance invariant (I1) across all journals.
- Elimination of fractional paise disputes between platform and merchants.
- Multi-currency conversions are explicitly deferred to future milestones.
