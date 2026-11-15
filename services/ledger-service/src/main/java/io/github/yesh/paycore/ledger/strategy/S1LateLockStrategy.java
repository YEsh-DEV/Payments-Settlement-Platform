package io.github.yesh.paycore.ledger.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Strategy S1: LATE_LOCK Holds hot-account locks for minimum possible duration by deferring hot
 * lock acquisition until immediately before database write and commit.
 */
@Component
public class S1LateLockStrategy implements PostingStrategy {

  private final AccountRepository accountRepository;
  private final JournalRepository journalRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;

  public S1LateLockStrategy(
      AccountRepository accountRepository,
      JournalRepository journalRepository,
      LedgerEntryRepository ledgerEntryRepository,
      OutboxEventRepository outboxEventRepository,
      ObjectMapper objectMapper) {
    this.accountRepository = accountRepository;
    this.journalRepository = journalRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public StrategyType getType() {
    return StrategyType.S1_LATE_LOCK;
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public PostingResult post(JournalRequest request) {
    // Idempotency check
    Optional<Journal> existing = journalRepository.findByIdempotencyKey(request.idempotencyKey());
    if (existing.isPresent()) {
      return new PostingResult(
          existing.get(), ledgerEntryRepository.findByJournalId(existing.get().getId()));
    }

    // Validate legs presence (Invariant I1: At least 2 legs)
    if (request.legs() == null || request.legs().size() < 2) {
      throw new PayCoreException(
          HttpStatus.BAD_REQUEST, "invalid_legs", "Journal must have at least two legs");
    }

    // Validate debits == credits
    Money totalDebits = Money.zero();
    Money totalCredits = Money.zero();
    for (JournalLeg leg : request.legs()) {
      if (leg.amount() == null || leg.amount().isZero() || leg.amount().isNegative()) {
        throw new PayCoreException(
            HttpStatus.BAD_REQUEST, "invalid_amount", "Leg amounts must be strictly positive");
      }
      if (leg.direction() == EntryDirection.DR) {
        totalDebits = totalDebits.plus(leg.amount());
      } else {
        totalCredits = totalCredits.plus(leg.amount());
      }
    }
    if (!totalDebits.equals(totalCredits)) {
      throw new PayCoreException(
          HttpStatus.BAD_REQUEST, "unbalanced_journal", "Debits do not equal credits");
    }

    Set<UUID> accountIds =
        request.legs().stream().map(JournalLeg::accountId).collect(Collectors.toSet());
    List<Account> preloaded = accountRepository.findByIdIn(accountIds);
    Map<UUID, Account> accountMap =
        preloaded.stream().collect(Collectors.toMap(Account::getId, a -> a));

    List<UUID> nonHotIds = new ArrayList<>();
    List<UUID> hotIds = new ArrayList<>();
    for (UUID id : accountIds) {
      if (accountMap.get(id).isHot()) {
        hotIds.add(id);
      } else {
        nonHotIds.add(id);
      }
    }

    Collections.sort(nonHotIds);
    Collections.sort(hotIds);

    // Step 1: Lock non-hot accounts early
    Map<UUID, Account> lockedAccounts = new HashMap<>();
    for (UUID id : nonHotIds) {
      Account locked =
          accountRepository
              .findByIdForUpdate(id)
              .orElseThrow(
                  () ->
                      new PayCoreException(
                          HttpStatus.NOT_FOUND, "account_not_found", "Account missing"));
      lockedAccounts.put(id, locked);
    }

    // Step 2: Validate non-hot account balances & sufficiency (Invariant I5)
    // Refunds and adjustments are permitted to reduce SELLER_PAYABLE below zero; payouts are not.
    if (request.type() != JournalType.REFUND && request.type() != JournalType.ADJUSTMENT) {
      for (UUID id : nonHotIds) {
        Account account = lockedAccounts.get(id);
        long delta = computeNetDelta(account.getType(), request.legs(), id);
        if (account.getCode().startsWith("MERCHANT_") || account.getType() == AccountType.LIABILITY) {
          if (account.getBalancePaise() + delta - account.getLockedBalancePaise() < 0) {
            throw new PayCoreException(
                HttpStatus.UNPROCESSABLE_ENTITY, "insufficient_funds", "Insufficient balance");
          }
        }
      }
    }

    // Step 3: LATE LOCK - Acquire hot-account locks at the very last moment
    for (UUID id : hotIds) {
      Account locked =
          accountRepository
              .findByIdForUpdate(id)
              .orElseThrow(
                  () ->
                      new PayCoreException(
                          HttpStatus.NOT_FOUND, "account_not_found", "Account missing"));
      lockedAccounts.put(id, locked);
    }

    // Step 4: Rapidly persist Journal and Entries
    Journal journal =
        new Journal(
            request.idempotencyKey(), request.referenceId(), request.type(), request.description());
    Journal savedJournal = journalRepository.save(journal);

    List<LedgerEntry> entries = new ArrayList<>();
    for (JournalLeg leg : request.legs()) {
      Account account = lockedAccounts.get(leg.accountId());
      long delta =
          (leg.direction() == EntryDirection.DR)
              ? (isDebitNormal(account.getType()) ? leg.amount().paise() : -leg.amount().paise())
              : (isDebitNormal(account.getType()) ? -leg.amount().paise() : leg.amount().paise());

      long newBalance = account.getBalancePaise() + delta;
      account.setBalancePaise(newBalance);
      accountRepository.save(account);

      LedgerEntry entry =
          new LedgerEntry(
              savedJournal.getId(),
              account.getId(),
              leg.direction(),
              leg.amount().paise(),
              newBalance);
      entries.add(ledgerEntryRepository.save(entry));
    }

    recordOutboxEvent(savedJournal, entries);
    return new PostingResult(savedJournal, entries);
  }

  private long computeNetDelta(AccountType type, List<JournalLeg> legs, UUID accountId) {
    long delta = 0L;
    boolean debitNormal = isDebitNormal(type);
    for (JournalLeg leg : legs) {
      if (leg.accountId().equals(accountId)) {
        delta +=
            (leg.direction() == EntryDirection.DR)
                ? (debitNormal ? leg.amount().paise() : -leg.amount().paise())
                : (debitNormal ? -leg.amount().paise() : leg.amount().paise());
      }
    }
    return delta;
  }

  private boolean isDebitNormal(AccountType type) {
    return type == AccountType.ASSET || type == AccountType.EXPENSE;
  }

  private void recordOutboxEvent(Journal journal, List<LedgerEntry> entries) {
    try {
      Map<String, Object> payload =
          Map.of(
              "journalId", journal.getId().toString(),
              "idempotencyKey", journal.getIdempotencyKey(),
              "referenceId", journal.getReferenceId() != null ? journal.getReferenceId() : "",
              "type", journal.getType().name(),
              "postedAt", journal.getPostedAt().toString(),
              "entriesCount", entries.size(),
              "strategy", "S1_LATE_LOCK");
      outboxEventRepository.save(
          new OutboxEvent(
              "JOURNAL",
              journal.getId().toString(),
              "LEDGER_POSTED",
              objectMapper.writeValueAsString(payload)));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize outbox event", e);
    }
  }
}
