package io.github.yesh.paycore.ledger.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.common.model.Money;
import io.github.yesh.paycore.ledger.domain.*;
import io.github.yesh.paycore.ledger.dto.JournalLeg;
import io.github.yesh.paycore.ledger.dto.JournalRequest;
import io.github.yesh.paycore.ledger.dto.PostingResult;
import io.github.yesh.paycore.ledger.engine.PostingEngine;
import io.github.yesh.paycore.ledger.repository.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HotAccountStrategiesTest {

  @Mock private AccountRepository accountRepository;
  @Mock private AccountStripeRepository accountStripeRepository;
  @Mock private JournalRepository journalRepository;
  @Mock private LedgerEntryRepository ledgerEntryRepository;
  @Mock private OutboxEventRepository outboxEventRepository;
  @Mock private PostingEngine postingEngine;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private S0PessimisticStrategy s0;
  private S1LateLockStrategy s1;
  private S2StripedStrategy s2;
  private S4LedgerOnlyStrategy s4;
  private PostingStrategyRouter router;

  @BeforeEach
  void setUp() {
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
    router = new PostingStrategyRouter(List.of(s0, s1, s2, s4), "S0_PESSIMISTIC");
  }

  @Test
  @DisplayName("Strategy S1 LATE_LOCK executes balanced journal and generates outbox event")
  void testS1LateLockExecution() {
    UUID nonHotId = UUID.randomUUID();
    UUID hotId = UUID.randomUUID();

    Account nonHot = new Account("MERCHANT_abc", AccountType.LIABILITY, false, 1);
    nonHot.setId(nonHotId);
    Account hot = new Account("USER_FUNDS", AccountType.ASSET, true, 10);
    hot.setId(hotId);

    when(journalRepository.findByScopeAndIdempotencyKey(any(), eq("idemp_s1")))
        .thenReturn(Optional.empty());
    when(accountRepository.findByIdIn(any())).thenReturn(List.of(nonHot, hot));
    when(accountRepository.findByIdForUpdate(nonHotId)).thenReturn(Optional.of(nonHot));
    when(accountRepository.findByIdForUpdate(hotId)).thenReturn(Optional.of(hot));
    when(journalRepository.save(any(Journal.class))).thenAnswer(inv -> inv.getArgument(0));
    when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));

    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(hotId, Money.ofPaise(5000)),
            JournalLeg.credit(nonHotId, Money.ofPaise(5000)));

    PostingResult result =
        s1.post(new JournalRequest("idemp_s1", "ref_s1", JournalType.PAYMENT, "s1 test", legs));

    assertThat(result.journal()).isNotNull();
    assertThat(result.entries()).hasSize(2);
    verify(outboxEventRepository).save(any(OutboxEvent.class));
  }

  @Test
  @DisplayName("Strategy S2 STRIPED routes hot-account balance mutation to account_stripes table")
  void testS2StripedExecution() {
    UUID nonHotId = UUID.randomUUID();
    UUID hotId = UUID.randomUUID();

    Account nonHot = new Account("MERCHANT_stripe", AccountType.LIABILITY, false, 1);
    nonHot.setId(nonHotId);
    Account hot = new Account("HOT_STRIPED", AccountType.ASSET, true, 10);
    hot.setId(hotId);

    when(journalRepository.findByScopeAndIdempotencyKey(any(), eq("idemp_s2")))
        .thenReturn(Optional.empty());
    when(accountRepository.findByIdIn(any())).thenReturn(List.of(nonHot, hot));
    when(accountRepository.findByIdForUpdate(nonHotId)).thenReturn(Optional.of(nonHot));
    when(accountStripeRepository.findStripeForUpdate(eq(hotId), anyInt()))
        .thenReturn(Optional.empty());
    when(accountStripeRepository.save(any(AccountStripe.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(journalRepository.save(any(Journal.class))).thenAnswer(inv -> inv.getArgument(0));
    when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));

    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(hotId, Money.ofPaise(2500)),
            JournalLeg.credit(nonHotId, Money.ofPaise(2500)));

    PostingResult result =
        s2.post(new JournalRequest("idemp_s2", "ref_s2", JournalType.PAYMENT, "s2 test", legs));

    assertThat(result.journal()).isNotNull();
    assertThat(result.entries()).hasSize(2);
    // Stripe repository was updated instead of master hot account row!
    verify(accountStripeRepository, atLeastOnce()).save(any(AccountStripe.class));
  }

  @Test
  @DisplayName(
      "Strategy S4 LEDGER_ONLY creates entries without locking or mutating hot account balance")
  void testS4LedgerOnlyExecution() {
    UUID nonHotId = UUID.randomUUID();
    UUID hotId = UUID.randomUUID();

    Account nonHot = new Account("MERCHANT_event", AccountType.LIABILITY, false, 1);
    nonHot.setId(nonHotId);
    Account hot = new Account("HOT_EVENT", AccountType.ASSET, true, 10);
    hot.setId(hotId);
    hot.setBalancePaise(100000L);

    when(journalRepository.findByScopeAndIdempotencyKey(any(), eq("idemp_s4")))
        .thenReturn(Optional.empty());
    when(accountRepository.findByIdIn(any())).thenReturn(List.of(nonHot, hot));
    when(accountRepository.findByIdForUpdate(nonHotId)).thenReturn(Optional.of(nonHot));
    when(journalRepository.save(any(Journal.class))).thenAnswer(inv -> inv.getArgument(0));
    when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));

    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(hotId, Money.ofPaise(1000)),
            JournalLeg.credit(nonHotId, Money.ofPaise(1000)));

    PostingResult result =
        s4.post(new JournalRequest("idemp_s4", "ref_s4", JournalType.PAYMENT, "s4 test", legs));

    assertThat(result.journal()).isNotNull();
    assertThat(result.entries()).hasSize(2);
    // Hot account row was NOT locked with findByIdForUpdate!
    verify(accountRepository, never()).findByIdForUpdate(hotId);
  }

  @Test
  @DisplayName("PostingStrategyRouter routes dynamically to selected strategy")
  void testStrategyRouter() {
    assertThat(router.getCurrentStrategy()).isEqualTo(StrategyType.S0_PESSIMISTIC);
    assertThat(router.getActiveStrategy().getType()).isEqualTo(StrategyType.S0_PESSIMISTIC);

    router.setStrategy(StrategyType.S2_STRIPED);
    assertThat(router.getActiveStrategy().getType()).isEqualTo(StrategyType.S2_STRIPED);

    router.setStrategy(StrategyType.S4_LEDGER_ONLY);
    assertThat(router.getActiveStrategy().getType()).isEqualTo(StrategyType.S4_LEDGER_ONLY);
  }
}
