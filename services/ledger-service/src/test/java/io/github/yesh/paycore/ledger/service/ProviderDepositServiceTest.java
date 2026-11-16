package io.github.yesh.paycore.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.github.yesh.paycore.ledger.domain.Account;
import io.github.yesh.paycore.ledger.domain.AccountType;
import io.github.yesh.paycore.ledger.domain.Journal;
import io.github.yesh.paycore.ledger.domain.JournalType;
import io.github.yesh.paycore.ledger.dto.CreateProviderDepositRequest;
import io.github.yesh.paycore.ledger.dto.JournalRequest;
import io.github.yesh.paycore.ledger.dto.PostingResult;
import io.github.yesh.paycore.ledger.dto.ProviderDepositResponse;
import io.github.yesh.paycore.ledger.idempotency.IdempotencyService;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
import io.github.yesh.paycore.ledger.strategy.PostingStrategy;
import io.github.yesh.paycore.ledger.strategy.PostingStrategyRouter;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProviderDepositServiceTest {

  @Mock private AccountRepository accountRepository;
  @Mock private PostingStrategyRouter strategyRouter;
  @Mock private PostingStrategy postingStrategy;
  @Mock private IdempotencyService idempotencyService;

  private ProviderDepositService providerDepositService;

  @BeforeEach
  void setUp() {
    providerDepositService =
        new ProviderDepositService(accountRepository, strategyRouter, idempotencyService);
  }

  @Test
  @DisplayName("Deposit with fees posts balanced journal: Debit BANK_CASH, Debit FEE_EXPENSE, Credit PROVIDER_RECEIVABLE")
  void testDepositWithFees() {
    Account bankCash = new Account(SystemAccountInitializer.BANK_CASH, AccountType.ASSET, false, 1);
    bankCash.setId(UUID.randomUUID());

    Account providerReceivable =
        new Account(SystemAccountInitializer.PROVIDER_RECEIVABLE, AccountType.ASSET, true, 16);
    providerReceivable.setId(UUID.randomUUID());

    Account feeExpense =
        new Account(SystemAccountInitializer.PROVIDER_FEE_EXPENSE, AccountType.EXPENSE, false, 1);
    feeExpense.setId(UUID.randomUUID());

    when(accountRepository.findByCode(SystemAccountInitializer.BANK_CASH))
        .thenReturn(Optional.of(bankCash));
    when(accountRepository.findByCode(SystemAccountInitializer.PROVIDER_RECEIVABLE))
        .thenReturn(Optional.of(providerReceivable));
    when(accountRepository.findByCode(SystemAccountInitializer.PROVIDER_FEE_EXPENSE))
        .thenReturn(Optional.of(feeExpense));
    when(strategyRouter.getActiveStrategy()).thenReturn(postingStrategy);

    Journal journal =
        new Journal("deposit:dep_101", "dep_101", JournalType.DEPOSIT, "Provider deposit");
    journal.setId(UUID.randomUUID());
    when(postingStrategy.post(any())).thenReturn(new PostingResult(journal, List.of()));

    CreateProviderDepositRequest request =
        new CreateProviderDepositRequest("dep_101", 98000L, 2000L, "BANK_REF_999", LocalDate.now());

    ProviderDepositResponse response =
        providerDepositService.doPostDeposit(request, "deposit:dep_101", "global");

    assertThat(response.depositId()).isEqualTo("dep_101");
    assertThat(response.netAmount()).isEqualTo(98000L);
    assertThat(response.feeAmount()).isEqualTo(2000L);
    assertThat(response.grossAmount()).isEqualTo(100000L);

    ArgumentCaptor<JournalRequest> captor = ArgumentCaptor.forClass(JournalRequest.class);
    verify(postingStrategy).post(captor.capture());

    JournalRequest captured = captor.getValue();
    assertThat(captured.type()).isEqualTo(JournalType.DEPOSIT);
    assertThat(captured.legs()).hasSize(3); // bankCash (debit), feeExpense (debit), providerReceivable (credit)

    long totalDebits =
        captured.legs().stream()
            .filter(l -> l.direction() == io.github.yesh.paycore.ledger.domain.EntryDirection.DR)
            .mapToLong(l -> l.amount().paise())
            .sum();
    long totalCredits =
        captured.legs().stream()
            .filter(l -> l.direction() == io.github.yesh.paycore.ledger.domain.EntryDirection.CR)
            .mapToLong(l -> l.amount().paise())
            .sum();
    assertThat(totalDebits).isEqualTo(totalCredits).isEqualTo(100000L);
  }
}
