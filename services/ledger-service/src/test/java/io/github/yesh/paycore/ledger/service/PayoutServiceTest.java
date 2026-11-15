package io.github.yesh.paycore.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.ledger.domain.*;
import io.github.yesh.paycore.ledger.dto.InitiatePayoutRequest;
import io.github.yesh.paycore.ledger.dto.PayoutResponse;
import io.github.yesh.paycore.ledger.dto.PostingResult;
import io.github.yesh.paycore.ledger.idempotency.IdempotencyService;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
import io.github.yesh.paycore.ledger.repository.IdempotencyRecordRepository;
import io.github.yesh.paycore.ledger.repository.PayoutRepository;
import io.github.yesh.paycore.ledger.repository.SellerRepository;
import io.github.yesh.paycore.ledger.strategy.PostingStrategy;
import io.github.yesh.paycore.ledger.strategy.PostingStrategyRouter;
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
class PayoutServiceTest {

  @Mock private PayoutRepository payoutRepository;
  @Mock private SellerRepository sellerRepository;
  @Mock private AccountRepository accountRepository;
  @Mock private PostingStrategyRouter strategyRouter;
  @Mock private PostingStrategy postingStrategy;
  @Mock private IdempotencyRecordRepository idempotencyRecordRepository;

  private PayoutService payoutService;

  @BeforeEach
  void setUp() {
    IdempotencyService idempotencyService =
        new IdempotencyService(idempotencyRecordRepository, new ObjectMapper());
    payoutService =
        new PayoutService(
            payoutRepository,
            sellerRepository,
            accountRepository,
            strategyRouter,
            idempotencyService);
  }

  @Test
  @DisplayName("Initiate payout moves funds from merchant account to SETTLEMENT_PAYABLE")
  void testInitiatePayoutSuccess() {
    String sellerId = "sel_payout_test";
    Seller seller = new Seller(sellerId, "Test Seller", "STANDARD", 200);

    Account merchant = new Account("MERCHANT_" + sellerId, AccountType.LIABILITY, false, 1);
    merchant.setId(UUID.randomUUID());
    merchant.setBalancePaise(100000L); // Rs 1000 available

    Account settlementPayable =
        new Account(SystemAccountInitializer.SETTLEMENT_PAYABLE, AccountType.LIABILITY, true, 10);
    settlementPayable.setId(UUID.randomUUID());

    when(payoutRepository.findByPayoutId("payout_1")).thenReturn(Optional.empty());
    when(sellerRepository.findBySellerId(sellerId)).thenReturn(Optional.of(seller));
    when(accountRepository.findByCode("MERCHANT_" + sellerId)).thenReturn(Optional.of(merchant));
    when(accountRepository.findByCode(SystemAccountInitializer.SETTLEMENT_PAYABLE))
        .thenReturn(Optional.of(settlementPayable));

    when(strategyRouter.getActiveStrategy()).thenReturn(postingStrategy);
    when(postingStrategy.post(any()))
        .thenReturn(
            new PostingResult(new Journal("idemp", "ref", JournalType.PAYOUT, "desc"), List.of()));
    when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

    InitiatePayoutRequest request =
        new InitiatePayoutRequest("payout_1", sellerId, 50000L); // Rs 500
    PayoutResponse response = payoutService.initiatePayout(request, null);

    assertThat(response.payoutId()).isEqualTo("payout_1");
    assertThat(response.amountPaise()).isEqualTo(50000L);
    assertThat(response.status()).isEqualTo("INITIATED");
    verify(postingStrategy).post(any());
  }

  @Test
  @DisplayName("Initiate payout with insufficient funds throws 422")
  void testInitiatePayoutInsufficientFunds() {
    String sellerId = "sel_poor";
    Seller seller = new Seller(sellerId, "Poor Seller", "STANDARD", 200);

    Account merchant = new Account("MERCHANT_" + sellerId, AccountType.LIABILITY, false, 1);
    merchant.setBalancePaise(1000L); // only Rs 10 available

    when(payoutRepository.findByPayoutId("payout_overdraft")).thenReturn(Optional.empty());
    when(sellerRepository.findBySellerId(sellerId)).thenReturn(Optional.of(seller));
    when(accountRepository.findByCode("MERCHANT_" + sellerId)).thenReturn(Optional.of(merchant));
    when(accountRepository.findByCode(SystemAccountInitializer.SETTLEMENT_PAYABLE))
        .thenReturn(Optional.of(new Account()));

    InitiatePayoutRequest request = new InitiatePayoutRequest("payout_overdraft", sellerId, 50000L);

    assertThatThrownBy(() -> payoutService.initiatePayout(request, null))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY);

    verify(payoutRepository, never()).save(any());
  }

  @Test
  @DisplayName("Confirm payout moves funds from SETTLEMENT_PAYABLE to USER_FUNDS and marks PAID")
  void testConfirmPayout() {
    Payout payout =
        new Payout("payout_1", "sel_1", 50000L, PayoutStatus.INITIATED, null, UUID.randomUUID());

    Account settlementPayable =
        new Account(SystemAccountInitializer.SETTLEMENT_PAYABLE, AccountType.LIABILITY, true, 10);
    settlementPayable.setId(UUID.randomUUID());

    Account userFunds =
        new Account(SystemAccountInitializer.USER_FUNDS_IN_TRANSIT, AccountType.ASSET, true, 10);
    userFunds.setId(UUID.randomUUID());

    when(payoutRepository.findByPayoutId("payout_1")).thenReturn(Optional.of(payout));
    when(accountRepository.findByCode(SystemAccountInitializer.SETTLEMENT_PAYABLE))
        .thenReturn(Optional.of(settlementPayable));
    when(accountRepository.findByCode(SystemAccountInitializer.USER_FUNDS_IN_TRANSIT))
        .thenReturn(Optional.of(userFunds));
    when(strategyRouter.getActiveStrategy()).thenReturn(postingStrategy);
    when(postingStrategy.post(any()))
        .thenReturn(
            new PostingResult(new Journal("idemp", "ref", JournalType.PAYOUT, "desc"), List.of()));
    when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

    PayoutResponse response = payoutService.confirmPayout("payout_1", "batch-nov-15");

    assertThat(response.status()).isEqualTo("PAID");
    assertThat(response.batchId()).isEqualTo("batch-nov-15");
    verify(postingStrategy).post(any());
  }

  @Test
  @DisplayName("Fail payout reverses funds from SETTLEMENT_PAYABLE back to merchant account")
  void testFailPayoutReversal() {
    Payout payout =
        new Payout(
            "payout_fail", "sel_fail", 50000L, PayoutStatus.INITIATED, null, UUID.randomUUID());

    Account settlementPayable =
        new Account(SystemAccountInitializer.SETTLEMENT_PAYABLE, AccountType.LIABILITY, true, 10);
    settlementPayable.setId(UUID.randomUUID());

    Account merchant = new Account("MERCHANT_sel_fail", AccountType.LIABILITY, false, 1);
    merchant.setId(UUID.randomUUID());

    when(payoutRepository.findByPayoutId("payout_fail")).thenReturn(Optional.of(payout));
    when(accountRepository.findByCode(SystemAccountInitializer.SETTLEMENT_PAYABLE))
        .thenReturn(Optional.of(settlementPayable));
    when(accountRepository.findByCode("MERCHANT_sel_fail")).thenReturn(Optional.of(merchant));
    when(strategyRouter.getActiveStrategy()).thenReturn(postingStrategy);
    when(postingStrategy.post(any()))
        .thenReturn(
            new PostingResult(new Journal("idemp", "ref", JournalType.PAYOUT, "desc"), List.of()));
    when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

    PayoutResponse response = payoutService.failPayout("payout_fail", "Account closed");

    assertThat(response.status()).isEqualTo("FAILED");
    verify(postingStrategy).post(any());
  }
}
