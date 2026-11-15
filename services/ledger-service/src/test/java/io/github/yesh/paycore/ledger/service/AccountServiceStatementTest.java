package io.github.yesh.paycore.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.yesh.paycore.ledger.domain.Account;
import io.github.yesh.paycore.ledger.domain.AccountType;
import io.github.yesh.paycore.ledger.domain.EntryDirection;
import io.github.yesh.paycore.ledger.domain.LedgerEntry;
import io.github.yesh.paycore.ledger.dto.StatementResponse;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
import io.github.yesh.paycore.ledger.repository.LedgerEntryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AccountServiceStatementTest {

  @Mock private AccountRepository accountRepository;
  @Mock private LedgerEntryRepository ledgerEntryRepository;

  private AccountService accountService;

  @BeforeEach
  void setUp() {
    accountService = new AccountService(accountRepository, ledgerEntryRepository);
  }

  @Test
  @DisplayName("Statement keyset pagination correctly flags hasMore and returns nextCursor")
  void testStatementKeysetPagination() {
    UUID accountId = UUID.randomUUID();
    Account account = new Account("MERCHANT_test", AccountType.LIABILITY, false, 1);
    account.setId(accountId);
    account.setBalancePaise(50000L);

    when(accountRepository.findByCode("MERCHANT_test")).thenReturn(Optional.of(account));

    // Create 3 entries when pageSize requested is 2 -> hasMore should be true!
    UUID journalId = UUID.randomUUID();
    LedgerEntry e1 = new LedgerEntry(journalId, accountId, EntryDirection.CR, 10000L, 10000L);
    setEntryId(e1, 101L);
    LedgerEntry e2 = new LedgerEntry(journalId, accountId, EntryDirection.CR, 20000L, 30000L);
    setEntryId(e2, 102L);
    LedgerEntry e3 = new LedgerEntry(journalId, accountId, EntryDirection.CR, 20000L, 50000L);
    setEntryId(e3, 103L);

    when(ledgerEntryRepository.findByAccountIdOrderByIdAsc(eq(accountId), any(Pageable.class)))
        .thenReturn(List.of(e1, e2, e3));

    StatementResponse response = accountService.getStatement("MERCHANT_test", null, 2);

    assertThat(response.entries()).hasSize(2);
    assertThat(response.entries().get(0).id()).isEqualTo(101L);
    assertThat(response.entries().get(1).id()).isEqualTo(102L);
    assertThat(response.hasMore()).isTrue();
    assertThat(response.nextCursor()).isEqualTo(102L);
  }

  private void setEntryId(LedgerEntry entry, Long id) {
    try {
      var field = LedgerEntry.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entry, id);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
