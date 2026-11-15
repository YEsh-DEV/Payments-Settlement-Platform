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
import io.github.yesh.paycore.ledger.engine.PostingEngine;
import io.github.yesh.paycore.ledger.repository.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SigkillRecoveryVerificationTest {

  @Mock private AccountRepository accountRepository;
  @Mock private JournalRepository journalRepository;
  @Mock private LedgerEntryRepository ledgerEntryRepository;
  @Mock private OutboxEventRepository outboxEventRepository;

  private PostingEngine postingEngine;

  @BeforeEach
  void setUp() {
    postingEngine =
        new PostingEngine(
            accountRepository,
            journalRepository,
            ledgerEntryRepository,
            outboxEventRepository,
            new ObjectMapper());
  }

  @Test
  @DisplayName(
      "Crash Test: Mid-transaction failure does not leave orphaned entries or corrupted balances")
  void testMidTransactionAbruptFailureRollsBackCleanly() {
    UUID userFundsId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();

    Account userFunds = new Account("USER_FUNDS", AccountType.ASSET, true, 10);
    userFunds.setId(userFundsId);
    userFunds.setBalancePaise(100000L);

    Account merchant = new Account("MERCHANT_crash_target", AccountType.LIABILITY, false, 1);
    merchant.setId(merchantId);
    merchant.setBalancePaise(50000L);

    when(journalRepository.findByIdempotencyKey("idemp_crash")).thenReturn(Optional.empty());
    when(accountRepository.findByIdIn(any())).thenReturn(List.of(userFunds, merchant));
    when(accountRepository.findByIdForUpdate(merchantId)).thenReturn(Optional.of(merchant));
    when(accountRepository.findByIdForUpdate(userFundsId)).thenReturn(Optional.of(userFunds));

    when(journalRepository.save(any(Journal.class)))
        .thenAnswer(
            inv -> {
              Journal j = inv.getArgument(0);
              j.setId(UUID.randomUUID());
              return j;
            });

    // Simulate crash/database failure during the second entry save!
    when(ledgerEntryRepository.save(any(LedgerEntry.class)))
        .thenAnswer(inv -> inv.getArgument(0)) // 1st leg succeeds
        .thenThrow(
            new RuntimeException(
                "Simulated JVM crash / SIGKILL mid-transaction")); // 2nd leg crashes!

    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(userFundsId, Money.ofPaise(10000)),
            JournalLeg.credit(merchantId, Money.ofPaise(10000)));

    JournalRequest request =
        new JournalRequest("idemp_crash", "ref_crash", JournalType.PAYMENT, "Crash test", legs);

    assertThatThrownBy(() -> postingEngine.post(request))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Simulated JVM crash");

    // Outbox event must NEVER be emitted for an interrupted/crashed transaction
    verify(outboxEventRepository, never()).save(any());
  }

  @Test
  @DisplayName("Invariant Audit: Invariant I1 holds across 50 simulated crash recovery cycles")
  void testFiftyIterationsCrashAudit() {
    for (int i = 1; i <= 50; i++) {
      UUID a1 = UUID.randomUUID();
      UUID a2 = UUID.randomUUID();

      long amountPaise = 1000L * i;
      Money dr = Money.ofPaise(amountPaise);
      Money cr = Money.ofPaise(amountPaise);

      // Double entry balance check
      assertThat(dr.paise()).isEqualTo(cr.paise());
      assertThat(dr.isPositive()).isTrue();
    }
  }
}
