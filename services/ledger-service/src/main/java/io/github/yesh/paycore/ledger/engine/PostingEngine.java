package io.github.yesh.paycore.ledger.engine;

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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Double-Entry Posting Engine implementing Strategy S0 (PESSIMISTIC) Enforces Invariants I1 through
 * I8 with deterministic global lock ordering.
 */
@Component
public class PostingEngine {

  private final AccountRepository accountRepository;
  private final JournalRepository journalRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  public PostingEngine(
      AccountRepository accountRepository,
      JournalRepository journalRepository,
      LedgerEntryRepository ledgerEntryRepository,
      OutboxEventRepository outboxEventRepository,
      ObjectMapper objectMapper) {
    this(
        accountRepository,
        journalRepository,
        ledgerEntryRepository,
        outboxEventRepository,
        objectMapper,
        new SimpleMeterRegistry());
  }

  @Autowired
  public PostingEngine(
      AccountRepository accountRepository,
      JournalRepository journalRepository,
      LedgerEntryRepository ledgerEntryRepository,
      OutboxEventRepository outboxEventRepository,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry) {
    this.accountRepository = accountRepository;
    this.journalRepository = journalRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public PostingResult post(JournalRequest request) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      PostingResult result = executePost(request);
      sample.stop(
          Timer.builder("paycore_ledger_post_duration_seconds")
              .tag("strategy", "S0_PESSIMISTIC")
              .tag("status", "SUCCESS")
              .description("Duration of ledger double-entry journal postings")
              .register(meterRegistry));
      return result;
    } catch (Exception e) {
      sample.stop(
          Timer.builder("paycore_ledger_post_duration_seconds")
              .tag("strategy", "S0_PESSIMISTIC")
              .tag("status", "FAILURE")
              .description("Duration of ledger double-entry journal postings")
              .register(meterRegistry));
      throw e;
    }
  }

  private PostingResult executePost(JournalRequest request) {
    // Invariant I6: Scoped Idempotent Execution
    String scope =
        request.scope() != null && !request.scope().isBlank() ? request.scope() : "global";
    Optional<Journal> existing =
        journalRepository.findByScopeAndIdempotencyKey(scope, request.idempotencyKey());
    if (existing.isPresent()) {
      List<LedgerEntry> entries = ledgerEntryRepository.findByJournalId(existing.get().getId());
      return new PostingResult(existing.get(), entries);
    }

    // Validate legs presence
    if (request.legs() == null || request.legs().size() < 2) {
      throw new PayCoreException(
          HttpStatus.BAD_REQUEST, "invalid_legs", "Journal must have at least two legs");
    }

    // Invariant I4: Positive Amounts & Invariant I3: Currency Homogeneity (handled by Money)
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

    // Invariant I1: Double-Entry Balance
    if (!totalDebits.equals(totalCredits)) {
      throw new PayCoreException(
          HttpStatus.BAD_REQUEST,
          "unbalanced_journal",
          "Debits ("
              + totalDebits.paise()
              + ") do not equal credits ("
              + totalCredits.paise()
              + ")");
    }

    // Invariant I7: Global Lock Ordering (non-hot sorted by UUID asc, then hot sorted by UUID asc)
    Set<UUID> accountIds =
        request.legs().stream().map(JournalLeg::accountId).collect(Collectors.toSet());

    List<Account> accountsUnordered = accountRepository.findByIdIn(accountIds);
    if (accountsUnordered.size() != accountIds.size()) {
      throw new PayCoreException(
          HttpStatus.NOT_FOUND, "account_not_found", "One or more accounts do not exist");
    }

    Map<UUID, Account> preloadedMap =
        accountsUnordered.stream().collect(Collectors.toMap(Account::getId, a -> a));

    List<UUID> nonHotIds = new ArrayList<>();
    List<UUID> hotIds = new ArrayList<>();

    for (UUID id : accountIds) {
      Account a = preloadedMap.get(id);
      if (a.isHot()) {
        hotIds.add(id);
      } else {
        nonHotIds.add(id);
      }
    }

    Collections.sort(nonHotIds);
    Collections.sort(hotIds);

    List<UUID> lockOrder = new ArrayList<>(nonHotIds);
    lockOrder.addAll(hotIds);

    // Acquire pessimistic locks in deterministic order
    Map<UUID, Account> lockedAccountMap = new HashMap<>();
    for (UUID id : lockOrder) {
      Account locked =
          accountRepository
              .findByIdForUpdate(id)
              .orElseThrow(
                  () ->
                      new PayCoreException(
                          HttpStatus.NOT_FOUND,
                          "account_not_found",
                          "Account " + id + " not found"));
      lockedAccountMap.put(id, locked);
    }

    // Invariant I5: Payout requests never exceed available SELLER_PAYABLE.
    // Note: SELLER_PAYABLE may go negative through refund or dispute/adjustment journals (the
    // seller owes the platform);
    // only payout requests and non-refund operations may never overdraw available payable.
    if (request.type() != JournalType.REFUND && request.type() != JournalType.ADJUSTMENT) {
      for (UUID id : accountIds) {
        Account account = lockedAccountMap.get(id);
        long netDelta = computeNetDelta(account.getType(), request.legs(), id);
        long prospectiveBalance = account.getBalancePaise() + netDelta;

        if (account.getCode().startsWith("MERCHANT_")
            || account.getType() == AccountType.LIABILITY) {
          long available = prospectiveBalance - account.getLockedBalancePaise();
          if (available < 0) {
            throw new PayCoreException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "insufficient_funds",
                "Insufficient available balance in account "
                    + account.getCode()
                    + " (required: "
                    + Math.abs(netDelta)
                    + ", available: "
                    + account.getAvailableBalancePaise()
                    + ")");
          }
        }
      }
    }

    // Persist Journal with caller scope
    Journal journal =
        new Journal(
            request.scope(),
            request.idempotencyKey(),
            request.referenceId(),
            request.type(),
            request.description());
    Journal savedJournal = journalRepository.save(journal);

    // Apply legs and create immutable Ledger Entries (Invariant I2 & I8)
    List<LedgerEntry> entries = new ArrayList<>();
    for (JournalLeg leg : request.legs()) {
      Account account = lockedAccountMap.get(leg.accountId());
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
      LedgerEntry savedEntry = ledgerEntryRepository.save(entry);
      entries.add(savedEntry);
    }

    // Publish Outbox Event for CDC / Kafka
    recordOutboxEvent(savedJournal, entries);

    return new PostingResult(savedJournal, entries);
  }

  private long computeNetDelta(AccountType type, List<JournalLeg> legs, UUID accountId) {
    long delta = 0L;
    boolean debitNormal = isDebitNormal(type);
    for (JournalLeg leg : legs) {
      if (leg.accountId().equals(accountId)) {
        if (leg.direction() == EntryDirection.DR) {
          delta += debitNormal ? leg.amount().paise() : -leg.amount().paise();
        } else {
          delta += debitNormal ? -leg.amount().paise() : leg.amount().paise();
        }
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
              "entriesCount", entries.size());

      String json = objectMapper.writeValueAsString(payload);
      OutboxEvent outboxEvent =
          new OutboxEvent("JOURNAL", journal.getId().toString(), "LEDGER_POSTED", json);
      outboxEventRepository.save(outboxEvent);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize outbox event payload", e);
    }
  }
}
