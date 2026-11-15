package io.github.yesh.paycore.ledger.service;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.ledger.domain.Account;
import io.github.yesh.paycore.ledger.domain.LedgerEntry;
import io.github.yesh.paycore.ledger.dto.AccountResponse;
import io.github.yesh.paycore.ledger.dto.LedgerEntryDto;
import io.github.yesh.paycore.ledger.dto.StatementResponse;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
import io.github.yesh.paycore.ledger.repository.LedgerEntryRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

  private final AccountRepository accountRepository;
  private final LedgerEntryRepository ledgerEntryRepository;

  public AccountService(
      AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository) {
    this.accountRepository = accountRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
  }

  @Transactional(readOnly = true)
  public AccountResponse getAccount(String code) {
    Account account =
        accountRepository
            .findByCode(code)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND,
                        "account_not_found",
                        "Account " + code + " not found"));
    return AccountResponse.from(account);
  }

  @Transactional(readOnly = true)
  public StatementResponse getStatement(String code, Long cursor, int limit) {
    Account account =
        accountRepository
            .findByCode(code)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND,
                        "account_not_found",
                        "Account " + code + " not found"));

    int pageSize = Math.min(Math.max(limit, 1), 200);
    List<LedgerEntry> rawEntries;

    if (cursor != null && cursor > 0) {
      rawEntries =
          ledgerEntryRepository.findByAccountIdAndIdGreaterThanOrderByIdAsc(
              account.getId(), cursor, PageRequest.of(0, pageSize + 1));
    } else {
      rawEntries =
          ledgerEntryRepository.findByAccountIdOrderByIdAsc(
              account.getId(), PageRequest.of(0, pageSize + 1));
    }

    boolean hasMore = rawEntries.size() > pageSize;
    List<LedgerEntry> pagedEntries = hasMore ? rawEntries.subList(0, pageSize) : rawEntries;
    Long nextCursor =
        pagedEntries.isEmpty() ? null : pagedEntries.get(pagedEntries.size() - 1).getId();

    List<LedgerEntryDto> dtos =
        pagedEntries.stream().map(LedgerEntryDto::from).collect(Collectors.toList());

    return new StatementResponse(account.getCode(), dtos, nextCursor, hasMore);
  }
}
