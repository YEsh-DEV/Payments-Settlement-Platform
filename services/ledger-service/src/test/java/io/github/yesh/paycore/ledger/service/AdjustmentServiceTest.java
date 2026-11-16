package io.github.yesh.paycore.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.yesh.paycore.ledger.domain.Account;
import io.github.yesh.paycore.ledger.domain.AccountType;
import io.github.yesh.paycore.ledger.domain.Journal;
import io.github.yesh.paycore.ledger.domain.JournalType;
import io.github.yesh.paycore.ledger.domain.Seller;
import io.github.yesh.paycore.ledger.dto.AdjustmentResponse;
import io.github.yesh.paycore.ledger.dto.CreateAdjustmentRequest;
import io.github.yesh.paycore.ledger.dto.PostingResult;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
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

@ExtendWith(MockitoExtension.class)
class AdjustmentServiceTest {

  @Mock private AccountRepository accountRepository;
  @Mock private SellerRepository sellerRepository;
  @Mock private PostingStrategyRouter strategyRouter;
  @Mock private PostingStrategy postingStrategy;

  private AdjustmentService adjustmentService;

  @BeforeEach
  void setUp() {
    adjustmentService = new AdjustmentService(accountRepository, sellerRepository, strategyRouter);
  }

  @Test
  @DisplayName("Credit adjustment debits ADJUSTMENT_EXPENSE and credits merchant account")
  void testCreditAdjustment() {
    String sellerId = "sel_adj";
    Seller seller = new Seller(sellerId, "Adj Seller", "STANDARD", 200);

    Account merchant = new Account("MERCHANT_" + sellerId, AccountType.LIABILITY, false, 1);
    merchant.setId(UUID.randomUUID());

    Account expense =
        new Account(SystemAccountInitializer.ADJUSTMENT_EXPENSE, AccountType.EXPENSE, false, 1);
    expense.setId(UUID.randomUUID());

    when(sellerRepository.findBySellerId(sellerId)).thenReturn(Optional.of(seller));
    when(accountRepository.findByCode("MERCHANT_" + sellerId)).thenReturn(Optional.of(merchant));
    when(accountRepository.findByCode(SystemAccountInitializer.ADJUSTMENT_EXPENSE))
        .thenReturn(Optional.of(expense));
    when(strategyRouter.getActiveStrategy()).thenReturn(postingStrategy);

    Journal journal =
        new Journal("adj_idemp", "ref_audit_1", JournalType.ADJUSTMENT, "Audit reason");
    journal.setId(UUID.randomUUID());
    when(postingStrategy.post(any())).thenReturn(new PostingResult(journal, List.of()));

    CreateAdjustmentRequest request =
        new CreateAdjustmentRequest(sellerId, "CREDIT", 5000L, "Dispute fee refund", "ref_audit_1");

    AdjustmentResponse response = adjustmentService.postAdjustment(request);

    assertThat(response.direction()).isEqualTo("CREDIT");
    assertThat(response.amountPaise()).isEqualTo(5000L);
    assertThat(response.reason()).isEqualTo("Dispute fee refund");
    verify(postingStrategy).post(any());
  }

  @Test
  @DisplayName("Debit adjustment debits merchant account and credits ADJUSTMENT_EXPENSE")
  void testDebitAdjustment() {
    String sellerId = "sel_adj_2";
    Seller seller = new Seller(sellerId, "Adj Seller 2", "STANDARD", 200);

    Account merchant = new Account("MERCHANT_" + sellerId, AccountType.LIABILITY, false, 1);
    merchant.setId(UUID.randomUUID());

    Account expense =
        new Account(SystemAccountInitializer.ADJUSTMENT_EXPENSE, AccountType.EXPENSE, false, 1);
    expense.setId(UUID.randomUUID());

    when(sellerRepository.findBySellerId(sellerId)).thenReturn(Optional.of(seller));
    when(accountRepository.findByCode("MERCHANT_" + sellerId)).thenReturn(Optional.of(merchant));
    when(accountRepository.findByCode(SystemAccountInitializer.ADJUSTMENT_EXPENSE))
        .thenReturn(Optional.of(expense));
    when(strategyRouter.getActiveStrategy()).thenReturn(postingStrategy);

    Journal journal = new Journal("adj_idemp_2", "ref_audit_2", JournalType.ADJUSTMENT, "Clawback");
    journal.setId(UUID.randomUUID());
    when(postingStrategy.post(any())).thenReturn(new PostingResult(journal, List.of()));

    CreateAdjustmentRequest request =
        new CreateAdjustmentRequest(sellerId, "DEBIT", 3000L, "Clawback penalty", "ref_audit_2");

    AdjustmentResponse response = adjustmentService.postAdjustment(request);

    assertThat(response.direction()).isEqualTo("DEBIT");
    assertThat(response.amountPaise()).isEqualTo(3000L);
    verify(postingStrategy).post(any());
  }

  @Test
  @DisplayName("System adjustment between system accounts posts balanced journal")
  void testSystemAdjustmentSuccess() {
    Account feeExpense =
        new Account(SystemAccountInitializer.PROVIDER_FEE_EXPENSE, AccountType.EXPENSE, false, 1);
    feeExpense.setId(UUID.randomUUID());

    Account providerReceivable =
        new Account(SystemAccountInitializer.PROVIDER_RECEIVABLE, AccountType.ASSET, true, 16);
    providerReceivable.setId(UUID.randomUUID());

    when(accountRepository.findByCode(SystemAccountInitializer.PROVIDER_FEE_EXPENSE))
        .thenReturn(Optional.of(feeExpense));
    when(accountRepository.findByCode(SystemAccountInitializer.PROVIDER_RECEIVABLE))
        .thenReturn(Optional.of(providerReceivable));
    when(strategyRouter.getActiveStrategy()).thenReturn(postingStrategy);

    Journal journal =
        new Journal("adjustment:adj_sys_1", "case_99", JournalType.ADJUSTMENT, "System adjustment");
    journal.setId(UUID.randomUUID());
    when(postingStrategy.post(any())).thenReturn(new PostingResult(journal, List.of()));

    CreateAdjustmentRequest request =
        new CreateAdjustmentRequest(
            "adj_sys_1",
            SystemAccountInitializer.PROVIDER_FEE_EXPENSE,
            SystemAccountInitializer.PROVIDER_RECEIVABLE,
            1500L,
            "case_99",
            "Fee variance resolution");

    AdjustmentResponse response = adjustmentService.postAdjustment(request);
    assertThat(response.amountPaise()).isEqualTo(1500L);
    assertThat(response.reason()).isEqualTo("Fee variance resolution");
    verify(postingStrategy).post(any());
  }

  @Test
  @DisplayName("System adjustment rejects seller accounts with 422")
  void testSystemAdjustmentRejectsSellerAccounts() {
    CreateAdjustmentRequest request =
        new CreateAdjustmentRequest(
            "adj_bad",
            "SELLER_PAYABLE_sel_1",
            SystemAccountInitializer.PROVIDER_RECEIVABLE,
            1500L,
            "case_100",
            "Illegal seller adjustment");

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> adjustmentService.postAdjustment(request))
        .isInstanceOf(io.github.yesh.paycore.common.error.PayCoreException.class)
        .hasFieldOrPropertyWithValue(
            "status", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
