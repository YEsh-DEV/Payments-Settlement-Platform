package io.github.yesh.paycore.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.yesh.paycore.ledger.domain.*;
import io.github.yesh.paycore.ledger.dto.IntegrityReportResponse;
import io.github.yesh.paycore.ledger.repository.*;
import io.github.yesh.paycore.ledger.strategy.PostingStrategyRouter;
import io.github.yesh.paycore.ledger.strategy.StrategyType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrityServiceTest {

  @Mock private AccountRepository accountRepository;
  @Mock private AccountStripeRepository accountStripeRepository;
  @Mock private AccountSnapshotRepository accountSnapshotRepository;
  @Mock private JournalRepository journalRepository;
  @Mock private LedgerEntryRepository ledgerEntryRepository;
  @Mock private PostingStrategyRouter strategyRouter;

  private SimpleMeterRegistry meterRegistry;
  private IntegrityService integrityService;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    integrityService =
        new IntegrityService(
            accountRepository,
            accountStripeRepository,
            accountSnapshotRepository,
            journalRepository,
            ledgerEntryRepository,
            strategyRouter,
            meterRegistry);
  }

  @Test
  @DisplayName("Should report healthy when all invariants I1, I3, I4 are satisfied")
  void testCleanIntegrityPasses() {
    when(strategyRouter.getCurrentStrategy()).thenReturn(StrategyType.S0_PESSIMISTIC);

    // I3: Global zero-sum
    when(ledgerEntryRepository.sumTotalByDirection(EntryDirection.DR)).thenReturn(100000L);
    when(ledgerEntryRepository.sumTotalByDirection(EntryDirection.CR)).thenReturn(100000L);

    // I4: Accounts
    UUID accId = UUID.randomUUID();
    Account account = new Account("PROVIDER_RECEIVABLE", AccountType.ASSET, true, 16);
    account.setId(accId);
    account.setBalancePaise(100000L);
    when(accountRepository.findAll()).thenReturn(List.of(account));
    when(ledgerEntryRepository.sumAmountByAccountIdAndDirection(accId, EntryDirection.DR))
        .thenReturn(100000L);
    when(ledgerEntryRepository.sumAmountByAccountIdAndDirection(accId, EntryDirection.CR))
        .thenReturn(0L);

    // I1: Sample journals
    UUID jrnId = UUID.randomUUID();
    Journal journal = new Journal("idemp_1", "ref_1", JournalType.PAYMENT, "Capture");
    journal.setId(jrnId);
    when(journalRepository.findAll()).thenReturn(List.of(journal));

    LedgerEntry drEntry = new LedgerEntry(jrnId, accId, EntryDirection.DR, 100000L, 100000L);
    LedgerEntry crEntry =
        new LedgerEntry(jrnId, UUID.randomUUID(), EntryDirection.CR, 100000L, 100000L);
    when(ledgerEntryRepository.findByJournalId(jrnId)).thenReturn(List.of(drEntry, crEntry));

    IntegrityReportResponse report = integrityService.runIntegrityCheck();

    assertThat(report.healthy()).isTrue();
    assertThat(report.violations()).isEmpty();
    assertThat(report.checkedAccountsCount()).isEqualTo(1);
    assertThat(report.checkedJournalsCount()).isEqualTo(1);
    assertThat(report.totalDebitsPaise()).isEqualTo(100000L);
    assertThat(report.totalCreditsPaise()).isEqualTo(100000L);
    assertThat(report.deltaPaise()).isZero();
    assertThat(meterRegistry.counter("ledger_integrity_violations_total").count()).isZero();
  }

  @Test
  @DisplayName("Invariant I3 Violation: Global debits != credits is detected and reported")
  void testGlobalDebitsCreditsMismatchFails() {
    when(strategyRouter.getCurrentStrategy()).thenReturn(StrategyType.S0_PESSIMISTIC);

    // Debits = 100000, Credits = 99500 (500 paise delta)
    when(ledgerEntryRepository.sumTotalByDirection(EntryDirection.DR)).thenReturn(100000L);
    when(ledgerEntryRepository.sumTotalByDirection(EntryDirection.CR)).thenReturn(99500L);
    when(accountRepository.findAll()).thenReturn(List.of());
    when(journalRepository.findAll()).thenReturn(List.of());

    IntegrityReportResponse report = integrityService.runIntegrityCheck();

    assertThat(report.healthy()).isFalse();
    assertThat(report.violations()).hasSize(1);
    assertThat(report.violations().get(0).invariant()).isEqualTo("I3");
    assertThat(report.deltaPaise()).isEqualTo(500L);
    assertThat(meterRegistry.counter("ledger_integrity_violations_total").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Invariant I4 Violation: Cached balance drift from ledger entries is detected")
  void testCachedBalanceDriftFails() {
    when(strategyRouter.getCurrentStrategy()).thenReturn(StrategyType.S0_PESSIMISTIC);
    when(ledgerEntryRepository.sumTotalByDirection(EntryDirection.DR)).thenReturn(50000L);
    when(ledgerEntryRepository.sumTotalByDirection(EntryDirection.CR)).thenReturn(50000L);

    UUID accId = UUID.randomUUID();
    Account account = new Account("MERCHANT_abc", AccountType.LIABILITY, false, 1);
    account.setId(accId);
    account.setBalancePaise(50000L); // Cached is 50000
    when(accountRepository.findAll()).thenReturn(List.of(account));

    // Entry ledger only sums to 45000
    when(ledgerEntryRepository.sumAmountByAccountIdAndDirection(accId, EntryDirection.DR))
        .thenReturn(0L);
    when(ledgerEntryRepository.sumAmountByAccountIdAndDirection(accId, EntryDirection.CR))
        .thenReturn(45000L);
    when(journalRepository.findAll()).thenReturn(List.of());

    IntegrityReportResponse report = integrityService.runIntegrityCheck();

    assertThat(report.healthy()).isFalse();
    assertThat(report.violations()).hasSize(1);
    assertThat(report.violations().get(0).invariant()).isEqualTo("I4");
    assertThat(report.violations().get(0).entityId()).isEqualTo("MERCHANT_abc");
    assertThat(meterRegistry.counter("ledger_integrity_violations_total").count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Invariant I4 (S2 Striped): Stripe sum mismatch against ledger entries is detected")
  void testStripedBalanceMismatchFails() {
    when(strategyRouter.getCurrentStrategy()).thenReturn(StrategyType.S2_STRIPED);
    when(ledgerEntryRepository.sumTotalByDirection(EntryDirection.DR)).thenReturn(20000L);
    when(ledgerEntryRepository.sumTotalByDirection(EntryDirection.CR)).thenReturn(20000L);

    UUID accId = UUID.randomUUID();
    Account hotAccount = new Account("PLATFORM_FEE_REVENUE", AccountType.REVENUE, true, 16);
    hotAccount.setId(accId);
    when(accountRepository.findAll()).thenReturn(List.of(hotAccount));

    // Calculated from entries: 20000 paise
    when(ledgerEntryRepository.sumAmountByAccountIdAndDirection(accId, EntryDirection.DR))
        .thenReturn(0L);
    when(ledgerEntryRepository.sumAmountByAccountIdAndDirection(accId, EntryDirection.CR))
        .thenReturn(20000L);

    // Sum of stripes: 18000 paise (stripe desync!)
    when(accountStripeRepository.sumBalanceByAccountId(accId)).thenReturn(18000L);
    when(journalRepository.findAll()).thenReturn(List.of());

    IntegrityReportResponse report = integrityService.runIntegrityCheck();

    assertThat(report.healthy()).isFalse();
    assertThat(report.violations()).hasSize(1);
    assertThat(report.violations().get(0).invariant()).isEqualTo("I4");
    assertThat(report.violations().get(0).entityType()).isEqualTo("ACCOUNT_STRIPES");
  }

  @Test
  @DisplayName("Invariant I1 Violation: Unbalanced journal is detected")
  void testUnbalancedJournalSampleFails() {
    when(strategyRouter.getCurrentStrategy()).thenReturn(StrategyType.S0_PESSIMISTIC);
    when(ledgerEntryRepository.sumTotalByDirection(EntryDirection.DR)).thenReturn(1000L);
    when(ledgerEntryRepository.sumTotalByDirection(EntryDirection.CR)).thenReturn(1000L);
    when(accountRepository.findAll()).thenReturn(List.of());

    UUID jrnId = UUID.randomUUID();
    Journal journal = new Journal("bad_jrn", "ref_bad", JournalType.PAYMENT, "Unbalanced journal");
    journal.setId(jrnId);
    when(journalRepository.findAll()).thenReturn(List.of(journal));

    // DR 1000, CR 900
    LedgerEntry drEntry =
        new LedgerEntry(jrnId, UUID.randomUUID(), EntryDirection.DR, 1000L, 1000L);
    LedgerEntry crEntry = new LedgerEntry(jrnId, UUID.randomUUID(), EntryDirection.CR, 900L, 900L);
    when(ledgerEntryRepository.findByJournalId(jrnId)).thenReturn(List.of(drEntry, crEntry));

    IntegrityReportResponse report = integrityService.runIntegrityCheck();

    assertThat(report.healthy()).isFalse();
    assertThat(report.violations()).hasSize(1);
    assertThat(report.violations().get(0).invariant()).isEqualTo("I1");
    assertThat(report.violations().get(0).entityType()).isEqualTo("JOURNAL_BALANCE");
  }
}
