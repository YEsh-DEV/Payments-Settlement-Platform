package io.github.yesh.paycore.ledger.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.common.model.Money;
import io.github.yesh.paycore.ledger.domain.*;
import io.github.yesh.paycore.ledger.dto.JournalLeg;
import io.github.yesh.paycore.ledger.dto.JournalRequest;
import io.github.yesh.paycore.ledger.dto.PostingResult;
import io.github.yesh.paycore.ledger.repository.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Strategy S4: LEDGER_ONLY Event-sourced append-only ledger entries without mutating or locking the
 * hot account balance row. Eliminates balance lock contention completely.
 */
@Component
public class S4LedgerOnlyStrategy implements PostingStrategy {

  private final AccountRepository accountRepository;
  private final JournalRepository journalRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;

  public S4LedgerOnlyStrategy(
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
    return StrategyType.S4_LEDGER_ONLY;
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public PostingResult post(JournalRequest request) {
    Optional<Journal> existing = journalRepository.findByIdempotencyKey(request.idempotencyKey());
    if (existing.isPresent()) {
      return new PostingResult(
          existing.get(), ledgerEntryRepository.findByJournalId(existing.get().getId()));
    }

    Money totalDebits = Money.zero();
    Money totalCredits = Money.zero();
    for (JournalLeg leg : request.legs()) {
      if (leg.amount() == null || leg.amount().isZero() || leg.amount().isNegative()) {
        throw new PayCoreException(
            HttpStatus.BAD_REQUEST, "invalid_amount", "Leg amounts must be positive");
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
    List<Account> accounts = accountRepository.findByIdIn(accountIds);
    Map<UUID, Account> accountMap =
        accounts.stream().collect(Collectors.toMap(Account::getId, a -> a));

    // Lock non-hot accounts only
    Map<UUID, Account> lockedNonHot = new HashMap<>();
    List<UUID> nonHotIds =
        accountIds.stream().filter(id -> !accountMap.get(id).isHot()).sorted().toList();
    for (UUID id : nonHotIds) {
      Account locked =
          accountRepository
              .findByIdForUpdate(id)
              .orElseThrow(
                  () ->
                      new PayCoreException(
                          HttpStatus.NOT_FOUND, "account_not_found", "Account missing"));
      lockedNonHot.put(id, locked);
    }

    Journal journal =
        new Journal(
            request.idempotencyKey(), request.referenceId(), request.type(), request.description());
    Journal savedJournal = journalRepository.save(journal);

    List<LedgerEntry> entries = new ArrayList<>();
    for (JournalLeg leg : request.legs()) {
      Account account = accountMap.get(leg.accountId());
      long runningBalance = 0L;

      if (!account.isHot()) {
        Account nonHot = lockedNonHot.get(account.getId());
        long delta =
            (leg.direction() == EntryDirection.DR)
                ? (isDebitNormal(nonHot.getType()) ? leg.amount().paise() : -leg.amount().paise())
                : (isDebitNormal(nonHot.getType()) ? -leg.amount().paise() : leg.amount().paise());
        long newBal = nonHot.getBalancePaise() + delta;
        nonHot.setBalancePaise(newBal);
        accountRepository.save(nonHot);
        runningBalance = newBal;
      } else {
        // S4: Append entry without updating master balance row
        runningBalance = account.getBalancePaise(); // Snapshot/baseline indicator
      }

      LedgerEntry entry =
          new LedgerEntry(
              savedJournal.getId(),
              account.getId(),
              leg.direction(),
              leg.amount().paise(),
              runningBalance);
      entries.add(ledgerEntryRepository.save(entry));
    }

    recordOutboxEvent(savedJournal, entries);
    return new PostingResult(savedJournal, entries);
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
              "strategy", "S4_LEDGER_ONLY");
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
