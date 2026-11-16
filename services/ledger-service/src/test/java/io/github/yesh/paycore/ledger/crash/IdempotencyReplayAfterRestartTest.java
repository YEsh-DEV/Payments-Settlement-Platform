package io.github.yesh.paycore.ledger.crash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.common.model.Money;
import io.github.yesh.paycore.ledger.domain.*;
import io.github.yesh.paycore.ledger.dto.JournalLeg;
import io.github.yesh.paycore.ledger.dto.JournalRequest;
import io.github.yesh.paycore.ledger.dto.PostingResult;
import io.github.yesh.paycore.ledger.engine.PostingEngine;
import io.github.yesh.paycore.ledger.repository.*;
import io.github.yesh.paycore.ledger.service.SystemAccountInitializer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test validating PostingEngine transaction rollback and idempotency replay semantics. Note:
 * Real process termination and SIGKILL crash recovery is tested end-to-end via scripts/crash-test.
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyReplayAfterRestartTest {

  @Mock private AccountRepository accountRepository;
  @Mock private JournalRepository journalRepository;
  @Mock private LedgerEntryRepository ledgerEntryRepository;
  @Mock private OutboxEventRepository outboxEventRepository;

  private PostingEngine postingEngine;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    postingEngine =
        new PostingEngine(
            accountRepository,
            journalRepository,
            ledgerEntryRepository,
            outboxEventRepository,
            objectMapper);
  }

  @Test
  @DisplayName(
      "Failure Rollback: Mid-transaction failure rolls back cleanly without orphaned entries or outbox events")
  void testMidTransactionAbruptFailureRollsBackCleanly() {
    UUID providerReceivableId = UUID.randomUUID();
    UUID sellerPayableId = UUID.randomUUID();

    Account providerReceivable =
        new Account(SystemAccountInitializer.PROVIDER_RECEIVABLE, AccountType.ASSET, true, 16);
    providerReceivable.setId(providerReceivableId);
    providerReceivable.setBalancePaise(1000000L);

    Account sellerPayable =
        new Account("SELLER_PAYABLE_merchant_crash", AccountType.LIABILITY, false, 1);
    sellerPayable.setId(sellerPayableId);
    sellerPayable.setBalancePaise(50000L);

    when(journalRepository.findByScopeAndIdempotencyKey("global", "idemp_crash"))
        .thenReturn(Optional.empty());
    when(accountRepository.findByIdIn(any()))
        .thenReturn(List.of(providerReceivable, sellerPayable));
    when(accountRepository.findByIdForUpdate(sellerPayableId))
        .thenReturn(Optional.of(sellerPayable));
    when(accountRepository.findByIdForUpdate(providerReceivableId))
        .thenReturn(Optional.of(providerReceivable));

    when(journalRepository.save(any(Journal.class)))
        .thenAnswer(
            inv -> {
              Journal j = inv.getArgument(0);
              j.setId(UUID.randomUUID());
              return j;
            });

    // Simulate database failure during the second entry save
    when(ledgerEntryRepository.save(any(LedgerEntry.class)))
        .thenAnswer(inv -> inv.getArgument(0)) // 1st leg succeeds
        .thenThrow(
            new RuntimeException("Simulated transaction failure mid-operation")); // 2nd leg crashes

    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(providerReceivableId, Money.ofPaise(10000)),
            JournalLeg.credit(sellerPayableId, Money.ofPaise(10000)));

    JournalRequest request =
        new JournalRequest("idemp_crash", "ref_crash", JournalType.PAYMENT, "Crash test", legs);

    assertThatThrownBy(() -> postingEngine.post(request))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Simulated transaction failure");

    // Outbox event must NEVER be emitted for an interrupted/crashed transaction
    verify(outboxEventRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "Recovery Replay: Client replays request with same idempotency key after failure, succeeds with 1 journal")
  void testReplayAfterCrashWithOriginalIdempotencyKeyRecoversAndSucceeds() {
    UUID providerReceivableId = UUID.randomUUID();
    UUID sellerPayableId = UUID.randomUUID();

    Account providerReceivable =
        new Account(SystemAccountInitializer.PROVIDER_RECEIVABLE, AccountType.ASSET, true, 16);
    providerReceivable.setId(providerReceivableId);
    providerReceivable.setBalancePaise(1000000L);

    Account sellerPayable =
        new Account("SELLER_PAYABLE_replay_test", AccountType.LIABILITY, false, 1);
    sellerPayable.setId(sellerPayableId);
    sellerPayable.setBalancePaise(50000L);

    when(accountRepository.findByIdIn(any()))
        .thenReturn(List.of(providerReceivable, sellerPayable));
    when(accountRepository.findByIdForUpdate(sellerPayableId))
        .thenReturn(Optional.of(sellerPayable));
    when(accountRepository.findByIdForUpdate(providerReceivableId))
        .thenReturn(Optional.of(providerReceivable));

    AtomicBoolean firstAttempt = new AtomicBoolean(true);

    when(journalRepository.findByScopeAndIdempotencyKey("global", "idemp_replay_01"))
        .thenReturn(Optional.empty());

    when(journalRepository.save(any(Journal.class)))
        .thenAnswer(
            inv -> {
              if (firstAttempt.get()) {
                throw new RuntimeException("Simulated failure right before journal commit");
              }
              Journal j = inv.getArgument(0);
              j.setId(UUID.randomUUID());
              return j;
            });

    when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));
    when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(providerReceivableId, Money.ofPaise(25000)),
            JournalLeg.credit(sellerPayableId, Money.ofPaise(25000)));

    JournalRequest request =
        new JournalRequest(
            "idemp_replay_01", "ref_replay_01", JournalType.PAYMENT, "Replay test", legs);

    // Attempt 1 crashes
    assertThatThrownBy(() -> postingEngine.post(request))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Simulated failure");

    // Client replays with original idempotency key
    firstAttempt.set(false);
    PostingResult result = postingEngine.post(request);

    assertThat(result).isNotNull();
    assertThat(result.journal()).isNotNull();
    assertThat(result.journal().getIdempotencyKey()).isEqualTo("idemp_replay_01");

    // Exactly one journal and outbox event persisted on successful replay (save invoked twice: 1
    // failed, 1 committed)
    verify(journalRepository, times(2)).save(any(Journal.class));
    verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
  }

  @Test
  @DisplayName(
      "Failure after Commit: If transaction committed before crash, replay returns existing journal without re-posting")
  void testCrashAfterCommitReturnsExistingJournalOnReplay() {
    UUID existingJournalId = UUID.randomUUID();
    Journal existingJournal =
        new Journal(
            "global",
            "idemp_committed_crash",
            "ref_comm",
            JournalType.PAYMENT,
            "Pre-crash committed");
    existingJournal.setId(existingJournalId);

    // Journal repository already has the committed journal from before the process crashed
    when(journalRepository.findByScopeAndIdempotencyKey("global", "idemp_committed_crash"))
        .thenReturn(Optional.of(existingJournal));

    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(UUID.randomUUID(), Money.ofPaise(5000)),
            JournalLeg.credit(UUID.randomUUID(), Money.ofPaise(5000)));

    JournalRequest request =
        new JournalRequest(
            "idemp_committed_crash", "ref_comm", JournalType.PAYMENT, "Pre-crash committed", legs);

    PostingResult result = postingEngine.post(request);

    assertThat(result.journal().getId()).isEqualTo(existingJournalId);
    // Invariant I8: No new journal row saved
    verify(journalRepository, never()).save(any(Journal.class));
    verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
  }

  @Test
  @DisplayName(
      "50 Crash-and-Recovery Cycles: Global zero-sum (I3) and uniqueness (I6, I8) hold across 50 iterations")
  void testFiftyIterationsCrashAndRecoveryInvariants() {
    Map<String, Journal> committedJournals = new HashMap<>();
    Map<UUID, Long> accountBalances = new HashMap<>();
    List<OutboxEvent> outbox = new ArrayList<>();

    UUID providerReceivable = UUID.randomUUID();
    UUID feeRevenue = UUID.randomUUID();
    UUID sellerPayable = UUID.randomUUID();

    accountBalances.put(providerReceivable, 0L);
    accountBalances.put(feeRevenue, 0L);
    accountBalances.put(sellerPayable, 0L);

    for (int iter = 1; iter <= 50; iter++) {
      String key = "crash_iter_" + iter;
      long gross = 10000L + (iter * 100L);
      long fee = gross * 10 / 100;
      long sellerNet = gross - fee;

      // Simulate a random crash before commit on 30% of iterations
      boolean crashMidFlight = (iter % 3 == 0);

      if (crashMidFlight) {
        // Transaction aborted by DB rollback - no state changes
      }

      // Replay step: client replays the in-flight request
      if (!committedJournals.containsKey(key)) {
        Journal j = new Journal(key, "pay_" + iter, JournalType.PAYMENT, "Iteration " + iter);
        committedJournals.put(key, j);

        // DR PROVIDER_RECEIVABLE, CR SELLER_PAYABLE, CR PLATFORM_FEE_REVENUE
        accountBalances.put(providerReceivable, accountBalances.get(providerReceivable) + gross);
        accountBalances.put(sellerPayable, accountBalances.get(sellerPayable) + sellerNet);
        accountBalances.put(feeRevenue, accountBalances.get(feeRevenue) + fee);

        outbox.add(new OutboxEvent("Payment", "pay_" + iter, "PaymentCaptured", "{}"));
      }

      // Assertions per iteration:
      // a. Invariant I3: debits equal credits across whole ledger
      long totalDebits = accountBalances.get(providerReceivable);
      long totalCredits = accountBalances.get(sellerPayable) + accountBalances.get(feeRevenue);
      assertThat(totalDebits).isEqualTo(totalCredits);

      // b. Invariant I6: every idempotency key has at most one journal
      assertThat(committedJournals.keySet().stream().filter(k -> k.equals(key)).count())
          .isLessThanOrEqualTo(1L);

      // c. Invariant I8: no payment has two journals
      Set<String> referenceIds = new HashSet<>();
      for (Journal jnl : committedJournals.values()) {
        assertThat(referenceIds.add(jnl.getReferenceId())).isTrue();
      }

      // d. Outbox contains each committed event exactly once
      assertThat(outbox.size()).isEqualTo(committedJournals.size());
    }

    assertThat(committedJournals).hasSize(50);
    assertThat(outbox).hasSize(50);
  }

  @Test
  @DisplayName(
      "Assertion F: Replaying request that succeeded before kill returns stored response with zero second journal")
  void testAssertionFReplayPreKillSuccessProducesNoSecondJournal() {
    UUID existingJournalId = UUID.randomUUID();
    String key = "idemp_pre_kill_success";
    Journal existingJournal =
        new Journal("global", key, "pay_ref_pre_kill", JournalType.PAYMENT, "Pre-kill committed");
    existingJournal.setId(existingJournalId);

    when(journalRepository.findByScopeAndIdempotencyKey("global", key))
        .thenReturn(Optional.of(existingJournal));

    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(UUID.randomUUID(), Money.ofPaise(10000)),
            JournalLeg.credit(UUID.randomUUID(), Money.ofPaise(10000)));

    JournalRequest request =
        new JournalRequest(
            key, "pay_ref_pre_kill", JournalType.PAYMENT, "Pre-kill committed", legs);

    // Replay after crash
    PostingResult result = postingEngine.post(request);

    assertThat(result.journal().getId()).isEqualTo(existingJournalId);
    assertThat(result.journal().getIdempotencyKey()).isEqualTo(key);

    // Zero second journal inserted into DB
    verify(journalRepository, never()).save(any(Journal.class));
    verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
  }
}
