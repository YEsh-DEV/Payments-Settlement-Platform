package io.github.yesh.paycore.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.ledger.domain.Account;
import io.github.yesh.paycore.ledger.domain.AccountType;
import io.github.yesh.paycore.ledger.domain.Hold;
import io.github.yesh.paycore.ledger.domain.HoldStatus;
import io.github.yesh.paycore.ledger.domain.Journal;
import io.github.yesh.paycore.ledger.domain.JournalType;
import io.github.yesh.paycore.ledger.dto.CaptureHoldRequest;
import io.github.yesh.paycore.ledger.dto.CreateHoldRequest;
import io.github.yesh.paycore.ledger.dto.HoldResponse;
import io.github.yesh.paycore.ledger.dto.PostingResult;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
import io.github.yesh.paycore.ledger.repository.HoldRepository;
import io.github.yesh.paycore.ledger.strategy.PostingStrategy;
import io.github.yesh.paycore.ledger.strategy.PostingStrategyRouter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class HoldServiceTest {

  @Mock private HoldRepository holdRepository;
  @Mock private AccountRepository accountRepository;
  @Mock private PostingStrategyRouter strategyRouter;
  @Mock private PostingStrategy postingStrategy;

  private HoldService holdService;

  @BeforeEach
  void setUp() {
    holdService = new HoldService(holdRepository, accountRepository, strategyRouter);
  }

  @Test
  @DisplayName("Create hold successfully locks balance and saves ACTIVE hold")
  void testCreateHoldSuccess() {
    UUID accountId = UUID.randomUUID();
    Account account = new Account("MERCHANT_hold", AccountType.LIABILITY, false, 1);
    account.setId(accountId);
    account.setBalancePaise(50000L); // Rs 500
    account.setLockedBalancePaise(10000L); // Rs 100 locked, Rs 400 available

    when(holdRepository.findByHoldId("hold_100")).thenReturn(Optional.empty());
    when(accountRepository.findByCodeForUpdate("MERCHANT_hold")).thenReturn(Optional.of(account));
    when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
    when(holdRepository.save(any(Hold.class)))
        .thenAnswer(
            inv -> {
              Hold h = inv.getArgument(0);
              return h;
            });

    CreateHoldRequest request =
        new CreateHoldRequest("hold_100", "MERCHANT_hold", 20000L, "Auth hold", 3600);
    HoldResponse response = holdService.createHold(request);

    assertThat(response.holdId()).isEqualTo("hold_100");
    assertThat(response.amountPaise()).isEqualTo(20000L);
    assertThat(response.status()).isEqualTo("ACTIVE");

    // Locked balance must increase from 10000 to 30000 paise
    assertThat(account.getLockedBalancePaise()).isEqualTo(30000L);
  }

  @Test
  @DisplayName("Create hold with insufficient available balance throws 422")
  void testCreateHoldInsufficientBalance() {
    Account account = new Account("MERCHANT_hold", AccountType.LIABILITY, false, 1);
    account.setBalancePaise(10000L);
    account.setLockedBalancePaise(8000L); // only 2000 available

    when(holdRepository.findByHoldId("hold_exceed")).thenReturn(Optional.empty());
    when(accountRepository.findByCodeForUpdate("MERCHANT_hold")).thenReturn(Optional.of(account));

    CreateHoldRequest request =
        new CreateHoldRequest("hold_exceed", "MERCHANT_hold", 5000L, "Too high", 3600);

    assertThatThrownBy(() -> holdService.createHold(request))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY);

    verify(holdRepository, never()).save(any());
  }

  @Test
  @DisplayName("Release hold decrements locked balance and marks status RELEASED")
  void testReleaseHold() {
    UUID accountId = UUID.randomUUID();
    Account account = new Account("MERCHANT_hold", AccountType.LIABILITY, false, 1);
    account.setId(accountId);
    account.setBalancePaise(50000L);
    account.setLockedBalancePaise(20000L);

    Hold hold =
        new Hold(
            "hold_rel",
            accountId,
            20000L,
            "Reason",
            HoldStatus.ACTIVE,
            Instant.now().plusSeconds(3600));

    when(holdRepository.findByHoldId("hold_rel")).thenReturn(Optional.of(hold));
    when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));
    when(holdRepository.save(any(Hold.class))).thenAnswer(inv -> inv.getArgument(0));

    HoldResponse response = holdService.releaseHold("hold_rel");

    assertThat(response.status()).isEqualTo("RELEASED");
    assertThat(account.getLockedBalancePaise()).isEqualTo(0L);
  }

  @Test
  @DisplayName("Capture hold unlocks held balance, posts journal, and marks status CAPTURED")
  void testCaptureHold() {
    UUID sourceAccountId = UUID.randomUUID();
    UUID destAccountId = UUID.randomUUID();

    Account source = new Account("MERCHANT_source", AccountType.LIABILITY, false, 1);
    source.setId(sourceAccountId);
    source.setBalancePaise(50000L);
    source.setLockedBalancePaise(15000L);

    Account dest = new Account("SETTLEMENT_PAYABLE", AccountType.LIABILITY, true, 10);
    dest.setId(destAccountId);

    Hold hold =
        new Hold(
            "hold_cap",
            sourceAccountId,
            15000L,
            "Reason",
            HoldStatus.ACTIVE,
            Instant.now().plusSeconds(3600));

    when(holdRepository.findByHoldId("hold_cap")).thenReturn(Optional.of(hold));
    when(accountRepository.findByIdForUpdate(sourceAccountId)).thenReturn(Optional.of(source));
    when(accountRepository.findByCode("SETTLEMENT_PAYABLE")).thenReturn(Optional.of(dest));
    when(strategyRouter.getActiveStrategy()).thenReturn(postingStrategy);
    when(postingStrategy.post(any()))
        .thenReturn(
            new PostingResult(new Journal("idemp", "ref", JournalType.CAPTURE, "desc"), List.of()));
    when(holdRepository.save(any(Hold.class))).thenAnswer(inv -> inv.getArgument(0));

    CaptureHoldRequest request = new CaptureHoldRequest("SETTLEMENT_PAYABLE", "idemp_cap");
    HoldResponse response = holdService.captureHold("hold_cap", request);

    assertThat(response.status()).isEqualTo("CAPTURED");
    assertThat(source.getLockedBalancePaise()).isEqualTo(0L);
    verify(postingStrategy).post(any());
  }

  @Test
  @DisplayName("Hold auto-release daemon sweeps and auto-releases expired holds")
  void testHoldAutoReleaseDaemon() {
    Hold expired1 =
        new Hold(
            "exp_1",
            UUID.randomUUID(),
            1000L,
            "Exp",
            HoldStatus.ACTIVE,
            Instant.now().minusSeconds(10));
    Hold expired2 =
        new Hold(
            "exp_2",
            UUID.randomUUID(),
            2000L,
            "Exp",
            HoldStatus.ACTIVE,
            Instant.now().minusSeconds(5));

    when(holdRepository.findByStatusAndExpiresAtBefore(eq(HoldStatus.ACTIVE), any(Instant.class)))
        .thenReturn(List.of(expired1, expired2));

    Account acc = new Account("ACC", AccountType.LIABILITY, false, 1);
    acc.setLockedBalancePaise(5000L);
    when(accountRepository.findByIdForUpdate(any())).thenReturn(Optional.of(acc));
    when(holdRepository.findByHoldId("exp_1")).thenReturn(Optional.of(expired1));
    when(holdRepository.findByHoldId("exp_2")).thenReturn(Optional.of(expired2));
    when(holdRepository.save(any(Hold.class))).thenAnswer(inv -> inv.getArgument(0));

    HoldAutoReleaseDaemon daemon = new HoldAutoReleaseDaemon(holdRepository, holdService);
    int swept = daemon.sweepExpiredHolds();

    assertThat(swept).isEqualTo(2);
    assertThat(expired1.getStatus()).isEqualTo(HoldStatus.RELEASED);
    assertThat(expired2.getStatus()).isEqualTo(HoldStatus.RELEASED);
  }
}
