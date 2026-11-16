package io.github.yesh.paycore.ledger.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.common.model.Money;
import io.github.yesh.paycore.ledger.domain.*;
import io.github.yesh.paycore.ledger.dto.JournalLeg;
import io.github.yesh.paycore.ledger.dto.JournalRequest;
import io.github.yesh.paycore.ledger.dto.PostingResult;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
import io.github.yesh.paycore.ledger.repository.JournalRepository;
import io.github.yesh.paycore.ledger.repository.LedgerEntryRepository;
import io.github.yesh.paycore.ledger.repository.OutboxEventRepository;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PostingEngineTest {

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
  @DisplayName("Should successfully post balanced journal, update balances, and create entries")
  void testPostBalancedJournalSuccess() {
    UUID userFundsId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    UUID feeId = UUID.randomUUID();

    Account userFunds = new Account("USER_FUNDS_IN_TRANSIT", AccountType.ASSET, true, 10);
    userFunds.setId(userFundsId);
    userFunds.setBalancePaise(0L);

    Account merchant = new Account("MERCHANT_seller1", AccountType.LIABILITY, false, 1);
    merchant.setId(merchantId);
    merchant.setBalancePaise(0L);

    Account fee = new Account("PLATFORM_FEE_REVENUE", AccountType.REVENUE, true, 10);
    fee.setId(feeId);
    fee.setBalancePaise(0L);

    when(journalRepository.findByScopeAndIdempotencyKey(any(), eq("idemp_100")))
        .thenReturn(Optional.empty());
    when(accountRepository.findByIdIn(any())).thenReturn(List.of(userFunds, merchant, fee));
    when(accountRepository.findByIdForUpdate(userFundsId)).thenReturn(Optional.of(userFunds));
    when(accountRepository.findByIdForUpdate(merchantId)).thenReturn(Optional.of(merchant));
    when(accountRepository.findByIdForUpdate(feeId)).thenReturn(Optional.of(fee));
    when(journalRepository.save(any(Journal.class)))
        .thenAnswer(
            inv -> {
              Journal j = inv.getArgument(0);
              j.setId(UUID.randomUUID());
              return j;
            });
    when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));

    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(userFundsId, Money.ofPaise(10000)), // Rs 100
            JournalLeg.credit(merchantId, Money.ofPaise(9800)), // Rs 98
            JournalLeg.credit(feeId, Money.ofPaise(200)) // Rs 2
            );

    JournalRequest request =
        new JournalRequest("idemp_100", "pay_ref_100", JournalType.PAYMENT, "Payment test", legs);

    PostingResult result = postingEngine.post(request);

    assertThat(result.journal()).isNotNull();
    assertThat(result.entries()).hasSize(3);

    // Asset account debited: balance increases from 0 to 10000
    assertThat(userFunds.getBalancePaise()).isEqualTo(10000L);
    // Liability account credited: balance increases from 0 to 9800
    assertThat(merchant.getBalancePaise()).isEqualTo(9800L);
    // Revenue account credited: balance increases from 0 to 200
    assertThat(fee.getBalancePaise()).isEqualTo(200L);

    verify(outboxEventRepository).save(any(OutboxEvent.class));
  }

  @Test
  @DisplayName("Invariant I1: Unbalanced journal must be rejected with 400 Bad Request")
  void testUnbalancedJournalThrows() {
    UUID acc1 = UUID.randomUUID();
    UUID acc2 = UUID.randomUUID();

    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(acc1, Money.ofPaise(10000)),
            JournalLeg.credit(acc2, Money.ofPaise(9900)) // 100 paise missing!
            );

    JournalRequest request =
        new JournalRequest("idemp_unbalanced", "ref_1", JournalType.PAYMENT, "Unbalanced", legs);

    assertThatThrownBy(() -> postingEngine.post(request))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);

    verify(journalRepository, never()).save(any());
  }

  @Test
  @DisplayName("Invariant I4: Non-positive amounts must be rejected")
  void testNonPositiveAmountThrows() {
    UUID acc1 = UUID.randomUUID();
    UUID acc2 = UUID.randomUUID();

    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(acc1, Money.ofPaise(0)), JournalLeg.credit(acc2, Money.ofPaise(0)));

    JournalRequest request =
        new JournalRequest("idemp_zero", "ref_zero", JournalType.PAYMENT, "Zero amount", legs);

    assertThatThrownBy(() -> postingEngine.post(request))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName(
      "Invariant I5: Insufficient funds on merchant payout throws 422 Unprocessable Entity")
  void testInsufficientFundsOnPayoutThrows() {
    UUID merchantId = UUID.randomUUID();
    UUID payoutTransitId = UUID.randomUUID();

    Account merchant = new Account("MERCHANT_poor", AccountType.LIABILITY, false, 1);
    merchant.setId(merchantId);
    merchant.setBalancePaise(500L); // only Rs 5 available

    Account payoutTransit = new Account("PAYOUT_IN_TRANSIT", AccountType.LIABILITY, false, 1);
    payoutTransit.setId(payoutTransitId);
    payoutTransit.setBalancePaise(0L);

    when(journalRepository.findByScopeAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
    when(accountRepository.findByIdIn(any())).thenReturn(List.of(merchant, payoutTransit));
    when(accountRepository.findByIdForUpdate(merchantId)).thenReturn(Optional.of(merchant));
    when(accountRepository.findByIdForUpdate(payoutTransitId))
        .thenReturn(Optional.of(payoutTransit));

    // Debit merchant for 1000 paise (Rs 10) when only 500 paise (Rs 5) available on PAYOUT
    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(merchantId, Money.ofPaise(1000)),
            JournalLeg.credit(payoutTransitId, Money.ofPaise(1000)));

    JournalRequest request =
        new JournalRequest(
            "idemp_overdraft",
            "ref_overdraft",
            JournalType.PAYOUT,
            "Payout overdraft attempt",
            legs);

    assertThatThrownBy(() -> postingEngine.post(request))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY);

    verify(ledgerEntryRepository, never()).save(any());
  }

  @Test
  @DisplayName("Invariant I5: Refunds permit SELLER_PAYABLE to go negative (seller owes platform)")
  void testRefundAllowsNegativeAvailableBalance() {
    UUID merchantId = UUID.randomUUID();
    UUID providerReceivableId = UUID.randomUUID();

    Account merchant = new Account("MERCHANT_overdrawn", AccountType.LIABILITY, false, 1);
    merchant.setId(merchantId);
    merchant.setBalancePaise(200L); // Rs 2 available

    Account providerReceivable = new Account("PROVIDER_RECEIVABLE", AccountType.ASSET, true, 16);
    providerReceivable.setId(providerReceivableId);
    providerReceivable.setBalancePaise(10000L);

    when(journalRepository.findByScopeAndIdempotencyKey(any(), eq("idemp_ref_neg")))
        .thenReturn(Optional.empty());
    when(accountRepository.findByIdIn(any())).thenReturn(List.of(merchant, providerReceivable));
    when(accountRepository.findByIdForUpdate(merchantId)).thenReturn(Optional.of(merchant));
    when(accountRepository.findByIdForUpdate(providerReceivableId))
        .thenReturn(Optional.of(providerReceivable));
    when(journalRepository.save(any(Journal.class)))
        .thenAnswer(
            inv -> {
              Journal j = inv.getArgument(0);
              j.setId(UUID.randomUUID());
              return j;
            });
    when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));

    // Refund of Rs 10 (1000 paise): merchant debited 1000 paise when balance was 200 -> new balance
    // = -800 paise
    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(merchantId, Money.ofPaise(1000)),
            JournalLeg.credit(providerReceivableId, Money.ofPaise(1000)));

    JournalRequest request =
        new JournalRequest(
            "idemp_ref_neg",
            "ref_neg_1",
            JournalType.REFUND,
            "Customer return negative payable",
            legs);

    PostingResult result = postingEngine.post(request);

    assertThat(result).isNotNull();
    assertThat(merchant.getBalancePaise()).isEqualTo(-800L);
    verify(outboxEventRepository).save(any(OutboxEvent.class));
  }

  @Test
  @DisplayName(
      "Invariant I6: Replaying same idempotency key returns existing journal without re-posting")
  void testIdempotentReplay() {
    Journal existing =
        new Journal("idemp_replay", "ref_replay", JournalType.PAYMENT, "Already posted");
    existing.setId(UUID.randomUUID());
    LedgerEntry entry1 =
        new LedgerEntry(existing.getId(), UUID.randomUUID(), EntryDirection.DR, 5000, 5000);

    when(journalRepository.findByScopeAndIdempotencyKey(any(), eq("idemp_replay")))
        .thenReturn(Optional.of(existing));
    when(ledgerEntryRepository.findByJournalId(existing.getId())).thenReturn(List.of(entry1));

    JournalRequest request =
        new JournalRequest(
            "idemp_replay",
            "ref_replay",
            JournalType.PAYMENT,
            "Replay",
            List.of(
                JournalLeg.debit(UUID.randomUUID(), Money.ofPaise(5000)),
                JournalLeg.credit(UUID.randomUUID(), Money.ofPaise(5000))));

    PostingResult result = postingEngine.post(request);

    assertThat(result.journal().getId()).isEqualTo(existing.getId());
    assertThat(result.entries()).hasSize(1);
    verify(accountRepository, never()).findByIdForUpdate(any());
    verify(journalRepository, never()).save(any());
  }

  @Test
  @DisplayName("Invariant I7: Global lock ordering sorts non-hot UUIDs first then hot UUIDs")
  void testGlobalLockOrdering() {
    UUID hot1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID hot2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    UUID nonHot1 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    UUID nonHot2 = UUID.fromString("00000000-0000-0000-0000-000000000004");

    Account h1 = new Account("HOT_1", AccountType.ASSET, true, 10);
    h1.setId(hot1);
    Account h2 = new Account("HOT_2", AccountType.ASSET, true, 10);
    h2.setId(hot2);
    Account nh1 = new Account("NH_1", AccountType.ASSET, false, 1);
    nh1.setId(nonHot1);
    Account nh2 = new Account("NH_2", AccountType.ASSET, false, 1);
    nh2.setId(nonHot2);

    when(journalRepository.findByScopeAndIdempotencyKey(any(), eq("idemp_lock_order")))
        .thenReturn(Optional.empty());
    when(accountRepository.findByIdIn(any())).thenReturn(List.of(h2, nh2, h1, nh1));
    when(accountRepository.findByIdForUpdate(any()))
        .thenAnswer(
            inv -> {
              UUID id = inv.getArgument(0);
              if (id.equals(hot1)) return Optional.of(h1);
              if (id.equals(hot2)) return Optional.of(h2);
              if (id.equals(nonHot1)) return Optional.of(nh1);
              if (id.equals(nonHot2)) return Optional.of(nh2);
              return Optional.empty();
            });
    when(journalRepository.save(any(Journal.class)))
        .thenAnswer(
            inv -> {
              Journal j = inv.getArgument(0);
              if (j.getId() == null) {
                j.setId(UUID.randomUUID());
              }
              return j;
            });

    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(hot2, Money.ofPaise(100)),
            JournalLeg.debit(nonHot2, Money.ofPaise(100)),
            JournalLeg.credit(hot1, Money.ofPaise(100)),
            JournalLeg.credit(nonHot1, Money.ofPaise(100)));

    postingEngine.post(
        new JournalRequest("idemp_lock_order", "ref", JournalType.PAYMENT, "desc", legs));

    // Lock ordering must be: nonHot1, nonHot2, hot1, hot2
    ArgumentCaptor<UUID> lockCaptor = ArgumentCaptor.forClass(UUID.class);
    verify(accountRepository, times(4)).findByIdForUpdate(lockCaptor.capture());

    List<UUID> captured = lockCaptor.getAllValues();
    assertThat(captured).containsExactly(nonHot1, nonHot2, hot1, hot2);
  }
}
