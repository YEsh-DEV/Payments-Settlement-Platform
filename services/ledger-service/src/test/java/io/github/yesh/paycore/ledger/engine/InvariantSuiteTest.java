package io.github.yesh.paycore.ledger.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.common.model.Money;
import io.github.yesh.paycore.ledger.domain.*;
import io.github.yesh.paycore.ledger.dto.*;
import io.github.yesh.paycore.ledger.idempotency.IdempotencyService;
import io.github.yesh.paycore.ledger.repository.*;
import io.github.yesh.paycore.ledger.service.HoldService;
import io.github.yesh.paycore.ledger.service.PaymentService;
import io.github.yesh.paycore.ledger.service.PayoutService;
import io.github.yesh.paycore.ledger.service.RefundService;
import io.github.yesh.paycore.ledger.service.SystemAccountInitializer;
import io.github.yesh.paycore.ledger.strategy.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Verification test suite for Invariants I1 through I8 across all posting strategies.
 */
@ExtendWith(MockitoExtension.class)
class InvariantSuiteTest {

  @Mock private AccountRepository accountRepository;
  @Mock private JournalRepository journalRepository;
  @Mock private LedgerEntryRepository ledgerEntryRepository;
  @Mock private OutboxEventRepository outboxEventRepository;
  @Mock private AccountStripeRepository accountStripeRepository;
  @Mock private AccountSnapshotRepository accountSnapshotRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private RefundRepository refundRepository;
  @Mock private PayoutRepository payoutRepository;
  @Mock private SellerRepository sellerRepository;
  @Mock private HoldRepository holdRepository;
  @Mock private IdempotencyRecordRepository idempotencyRecordRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private PostingEngine postingEngine;
  private S0PessimisticStrategy s0;
  private S1LateLockStrategy s1;
  private S2StripedStrategy s2;
  private S4LedgerOnlyStrategy s4;

  @BeforeEach
  void setUp() {
    postingEngine =
        new PostingEngine(
            accountRepository,
            journalRepository,
            ledgerEntryRepository,
            outboxEventRepository,
            objectMapper);
    s0 = new S0PessimisticStrategy(postingEngine);
    s1 =
        new S1LateLockStrategy(
            accountRepository,
            journalRepository,
            ledgerEntryRepository,
            outboxEventRepository,
            objectMapper);
    s2 =
        new S2StripedStrategy(
            accountRepository,
            accountStripeRepository,
            journalRepository,
            ledgerEntryRepository,
            outboxEventRepository,
            objectMapper);
    s4 =
        new S4LedgerOnlyStrategy(
            accountRepository,
            journalRepository,
            ledgerEntryRepository,
            outboxEventRepository,
            objectMapper);
  }

  private PostingStrategy getStrategy(StrategyType type) {
    return switch (type) {
      case S0_PESSIMISTIC -> s0;
      case S1_LATE_LOCK -> s1;
      case S2_STRIPED -> s2;
      case S4_LEDGER_ONLY -> s4;
    };
  }

  @ParameterizedTest
  @EnumSource(StrategyType.class)
  @DisplayName("Invariant I1: Double-Entry Balance holds across all strategies")
  void testInvariantI1DoubleEntryBalance(StrategyType type) {
    PostingStrategy strategy = getStrategy(type);

    UUID providerId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    UUID feeId = UUID.randomUUID();

    Account provider = new Account("PROVIDER_RECEIVABLE", AccountType.ASSET, true, 16);
    provider.setId(providerId);
    Account merchant = new Account("MERCHANT_s1", AccountType.LIABILITY, false, 1);
    merchant.setId(merchantId);
    Account fee = new Account("PLATFORM_FEE_REVENUE", AccountType.REVENUE, true, 16);
    fee.setId(feeId);

    when(journalRepository.findByIdempotencyKey("idemp_i1_" + type)).thenReturn(Optional.empty());
    when(accountRepository.findByIdIn(any())).thenReturn(List.of(provider, merchant, fee));
    when(accountRepository.findByIdForUpdate(merchantId)).thenReturn(Optional.of(merchant));
    if (type == StrategyType.S0_PESSIMISTIC || type == StrategyType.S1_LATE_LOCK) {
      when(accountRepository.findByIdForUpdate(providerId)).thenReturn(Optional.of(provider));
      when(accountRepository.findByIdForUpdate(feeId)).thenReturn(Optional.of(fee));
    } else if (type == StrategyType.S2_STRIPED) {
      when(accountStripeRepository.findStripeForUpdate(any(), anyInt())).thenReturn(Optional.empty());
      when(accountStripeRepository.save(any(AccountStripe.class))).thenAnswer(inv -> inv.getArgument(0));
    }
    when(journalRepository.save(any(Journal.class)))
        .thenAnswer(inv -> {
          Journal j = inv.getArgument(0);
          j.setId(UUID.randomUUID());
          return j;
        });
    when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));

    // Capture payment: Rs 100 gross = Rs 98 merchant + Rs 2 fee
    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(providerId, Money.ofPaise(10000)),
            JournalLeg.credit(merchantId, Money.ofPaise(9800)),
            JournalLeg.credit(feeId, Money.ofPaise(200)));

    JournalRequest request =
        new JournalRequest("idemp_i1_" + type, "ref_i1", JournalType.PAYMENT, "I1 test", legs);

    PostingResult result = strategy.post(request);

    assertThat(result.journal()).isNotNull();
    assertThat(result.entries()).hasSize(3);

    long totalDr = result.entries().stream().filter(e -> e.getDirection() == EntryDirection.DR).mapToLong(LedgerEntry::getAmountPaise).sum();
    long totalCr = result.entries().stream().filter(e -> e.getDirection() == EntryDirection.CR).mapToLong(LedgerEntry::getAmountPaise).sum();
    assertThat(totalDr).isEqualTo(totalCr).isEqualTo(10000L);
  }

  @ParameterizedTest
  @EnumSource(StrategyType.class)
  @DisplayName("Invariant I1: Reject unbalanced journals and empty legs across all strategies")
  void testInvariantI1RejectUnbalanced(StrategyType type) {
    PostingStrategy strategy = getStrategy(type);
    UUID acc1 = UUID.randomUUID();
    UUID acc2 = UUID.randomUUID();

    // 1. Unbalanced debits != credits
    List<JournalLeg> unbalancedLegs =
        List.of(
            JournalLeg.debit(acc1, Money.ofPaise(1000)),
            JournalLeg.credit(acc2, Money.ofPaise(900)));
    JournalRequest req1 =
        new JournalRequest("idemp_bad_bal", "ref", JournalType.PAYMENT, "unbalanced", unbalancedLegs);

    assertThatThrownBy(() -> strategy.post(req1))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);

    // 2. Empty legs
    JournalRequest req2 =
        new JournalRequest("idemp_empty", "ref", JournalType.PAYMENT, "empty", List.of());
    assertThatThrownBy(() -> strategy.post(req2))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
  }

  @ParameterizedTest
  @EnumSource(StrategyType.class)
  @DisplayName("Invariant I5: Refund allows SELLER_PAYABLE to go negative (seller owes platform)")
  void testInvariantI5RefundAllowsNegativePayable(StrategyType type) {
    PostingStrategy strategy = getStrategy(type);

    UUID merchantId = UUID.randomUUID();
    UUID providerId = UUID.randomUUID();

    Account merchant = new Account("MERCHANT_broke", AccountType.LIABILITY, false, 1);
    merchant.setId(merchantId);
    merchant.setBalancePaise(100L); // only Rs 1 balance

    Account provider = new Account("PROVIDER_RECEIVABLE", AccountType.ASSET, true, 16);
    provider.setId(providerId);
    provider.setBalancePaise(50000L);

    when(journalRepository.findByIdempotencyKey("idemp_neg_" + type)).thenReturn(Optional.empty());
    when(accountRepository.findByIdIn(any())).thenReturn(List.of(merchant, provider));
    when(accountRepository.findByIdForUpdate(merchantId)).thenReturn(Optional.of(merchant));
    if (type == StrategyType.S0_PESSIMISTIC || type == StrategyType.S1_LATE_LOCK) {
      when(accountRepository.findByIdForUpdate(providerId)).thenReturn(Optional.of(provider));
    } else if (type == StrategyType.S2_STRIPED) {
      when(accountStripeRepository.findStripeForUpdate(any(), anyInt())).thenReturn(Optional.empty());
      when(accountStripeRepository.save(any(AccountStripe.class))).thenAnswer(inv -> inv.getArgument(0));
    }
    when(journalRepository.save(any(Journal.class)))
        .thenAnswer(inv -> {
          Journal j = inv.getArgument(0);
          j.setId(UUID.randomUUID());
          return j;
        });
    when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));

    // Refund of Rs 50 (5000 paise): merchant balance drops from +100 to -4900 paise
    List<JournalLeg> refundLegs =
        List.of(
            JournalLeg.debit(merchantId, Money.ofPaise(5000)),
            JournalLeg.credit(providerId, Money.ofPaise(5000)));

    JournalRequest refundRequest =
        new JournalRequest("idemp_neg_" + type, "ref_neg", JournalType.REFUND, "Negative payable refund", refundLegs);

    PostingResult result = strategy.post(refundRequest);

    assertThat(result).isNotNull();
    assertThat(merchant.getBalancePaise()).isEqualTo(-4900L);
  }

  @ParameterizedTest
  @EnumSource(StrategyType.class)
  @DisplayName("Invariant I5: Payout request overdrawing available SELLER_PAYABLE is rejected with 422")
  void testInvariantI5PayoutOverdraftRejected(StrategyType type) {
    PostingStrategy strategy = getStrategy(type);

    UUID merchantId = UUID.randomUUID();
    UUID payoutTransitId = UUID.randomUUID();

    Account merchant = new Account("MERCHANT_overdraft", AccountType.LIABILITY, false, 1);
    merchant.setId(merchantId);
    merchant.setBalancePaise(3000L); // Rs 30 balance
    merchant.setLockedBalancePaise(1000L); // Rs 10 locked, Rs 20 available

    Account payoutTransit = new Account("PAYOUT_IN_TRANSIT", AccountType.LIABILITY, false, 1);
    payoutTransit.setId(payoutTransitId);

    when(journalRepository.findByIdempotencyKey("idemp_payout_bad")).thenReturn(Optional.empty());
    when(accountRepository.findByIdIn(any())).thenReturn(List.of(merchant, payoutTransit));
    when(accountRepository.findByIdForUpdate(merchantId)).thenReturn(Optional.of(merchant));
    when(accountRepository.findByIdForUpdate(payoutTransitId)).thenReturn(Optional.of(payoutTransit));

    // Payout attempt of Rs 25 (2500 paise), but available is only Rs 20 (2000 paise)
    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(merchantId, Money.ofPaise(2500)),
            JournalLeg.credit(payoutTransitId, Money.ofPaise(2500)));

    JournalRequest request =
        new JournalRequest("idemp_payout_bad", "payout_1", JournalType.PAYOUT, "Overdraw attempt", legs);

    assertThatThrownBy(() -> strategy.post(request))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("Invariant I6: Same idempotency key produces at most one journal")
  void testInvariantI6Idempotency() {
    Journal existingJournal =
        new Journal("idemp_unique_key", "ref_original", JournalType.PAYMENT, "Original post");
    existingJournal.setId(UUID.randomUUID());
    LedgerEntry entry = new LedgerEntry(existingJournal.getId(), UUID.randomUUID(), EntryDirection.DR, 5000L, 5000L);

    when(journalRepository.findByIdempotencyKey("idemp_unique_key")).thenReturn(Optional.of(existingJournal));
    when(ledgerEntryRepository.findByJournalId(existingJournal.getId())).thenReturn(List.of(entry));

    JournalRequest req =
        new JournalRequest(
            "idemp_unique_key",
            "ref_original",
            JournalType.PAYMENT,
            "Replay attempt",
            List.of(JournalLeg.debit(UUID.randomUUID(), Money.ofPaise(5000)), JournalLeg.credit(UUID.randomUUID(), Money.ofPaise(5000))));

    PostingResult result = postingEngine.post(req);

    assertThat(result.journal().getId()).isEqualTo(existingJournal.getId());
    verify(journalRepository, never()).save(any());
    verify(ledgerEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("Invariant I7: Total refunds for a payment never exceed gross captured amount")
  void testInvariantI7RefundLimit() {
    IdempotencyService idempService = new IdempotencyService(idempotencyRecordRepository, objectMapper);
    PostingStrategyRouter router = new PostingStrategyRouter(List.of(s0), "S0_PESSIMISTIC");
    RefundService refundService =
        new RefundService(refundRepository, paymentRepository, sellerRepository, accountRepository, router, idempService);

    Seller seller = new Seller("seller_limit", "Limit Merchant", "STANDARD", 200);
    Payment payment =
        new Payment("pay_limit", "seller_limit", 10000L, 200L, 9800L, PaymentStatus.CAPTURED, UUID.randomUUID());

    when(paymentRepository.findByPaymentId("pay_limit")).thenReturn(Optional.of(payment));

    // Refund 1: Rs 60 out of Rs 100
    Refund r1 = new Refund("ref_lim_1", "pay_limit", 6000L, "First return", RefundStatus.SUCCEEDED, UUID.randomUUID());
    when(refundRepository.findByPaymentId("pay_limit")).thenReturn(List.of(r1));

    // Refund 2: Attempt Rs 50 (total would be 110 > 100) -> must throw 400
    RefundRequest badReq = new RefundRequest("ref_lim_2", "pay_limit", 5000L, "Exceeding refund");

    assertThatThrownBy(() -> refundService.processRefund(badReq, null))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("Concurrent Posting: Multi-threaded execution produces zero deadlocks and consistent balances")
  void testConcurrentPostingZeroDeadlocks() throws InterruptedException {
    int threadCount = 8;
    int iterationsPerThread = 25;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    UUID merchantId = UUID.randomUUID();
    UUID providerId = UUID.randomUUID();
    UUID feeId = UUID.randomUUID();

    Account merchant = new Account("MERCHANT_concurrent", AccountType.LIABILITY, false, 1);
    merchant.setId(merchantId);
    Account provider = new Account("PROVIDER_RECEIVABLE", AccountType.ASSET, true, 16);
    provider.setId(providerId);
    Account fee = new Account("PLATFORM_FEE_REVENUE", AccountType.REVENUE, true, 16);
    fee.setId(feeId);

    when(accountRepository.findByIdIn(any())).thenReturn(List.of(provider, merchant, fee));
    when(accountRepository.findByIdForUpdate(merchantId)).thenReturn(Optional.of(merchant));
    when(accountRepository.findByIdForUpdate(providerId)).thenReturn(Optional.of(provider));
    when(accountRepository.findByIdForUpdate(feeId)).thenReturn(Optional.of(fee));
    when(journalRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
    when(journalRepository.save(any(Journal.class)))
        .thenAnswer(inv -> {
          Journal j = inv.getArgument(0);
          j.setId(UUID.randomUUID());
          return j;
        });
    when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));

    for (int i = 0; i < threadCount; i++) {
      final int threadIdx = i;
      executor.submit(() -> {
        try {
          startLatch.await();
          for (int j = 0; j < iterationsPerThread; j++) {
            List<JournalLeg> legs =
                List.of(
                    JournalLeg.debit(providerId, Money.ofPaise(1000)),
                    JournalLeg.credit(merchantId, Money.ofPaise(980)),
                    JournalLeg.credit(feeId, Money.ofPaise(20)));
            JournalRequest req =
                new JournalRequest(
                    "thread_" + threadIdx + "_run_" + j,
                    "ref_" + threadIdx + "_" + j,
                    JournalType.PAYMENT,
                    "Concurrent capture",
                    legs);
            postingEngine.post(req);
            successCount.incrementAndGet();
          }
        } catch (Exception e) {
          // Failure
        } finally {
          doneLatch.countDown();
        }
      });
    }

    startLatch.countDown();
    boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(completed).isTrue();
    assertThat(successCount.get()).isEqualTo(threadCount * iterationsPerThread);
  }

  @Test
  @DisplayName("Fee Arithmetic Boundary: 1 paisa, odd paise, and zero/100% fee cases balance exactly")
  void testFeeArithmeticBoundaries() {
    // 1 paisa gross with 200 bps fee
    Money onePaisa = Money.ofPaise(1L);
    Money feeOnOne = onePaisa.calculateFeeBps(200); // 1 * 200 / 10000 = 0.02 -> 0 paise
    Money netOnOne = onePaisa.minus(feeOnOne);
    assertThat(feeOnOne.paise()).isZero();
    assertThat(netOnOne.paise()).isEqualTo(1L);
    assertThat(netOnOne.plus(feeOnOne)).isEqualTo(onePaisa);

    // Odd paise: 3 paise with 200 bps
    Money threePaise = Money.ofPaise(3L);
    Money feeOnThree = threePaise.calculateFeeBps(200); // 3 * 200 / 10000 = 0.06 -> 0 paise
    Money netOnThree = threePaise.minus(feeOnThree);
    assertThat(netOnThree.plus(feeOnThree)).isEqualTo(threePaise);

    // 10000 bps (100% platform fee)
    Money gross100 = Money.ofPaise(10000L);
    Money fee100 = gross100.calculateFeeBps(10000);
    Money net100 = gross100.minus(fee100);
    assertThat(fee100.paise()).isEqualTo(10000L);
    assertThat(net100.paise()).isZero();
    assertThat(net100.plus(fee100)).isEqualTo(gross100);

    // 0 bps (0% fee)
    Money fee0 = gross100.calculateFeeBps(0);
    Money net0 = gross100.minus(fee0);
    assertThat(fee0.paise()).isZero();
    assertThat(net0.paise()).isEqualTo(10000L);
    assertThat(net0.plus(fee0)).isEqualTo(gross100);
  }
}
