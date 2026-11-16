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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Strategy S2: STRIPED Distributes hot-account balance mutations across N sub-stripes, reducing row
 * contention by a factor of N while preserving overall balance invariant.
 */
@Component
public class S2StripedStrategy implements PostingStrategy {

  private final AccountRepository accountRepository;
  private final AccountStripeRepository accountStripeRepository;
  private final JournalRepository journalRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;

  public S2StripedStrategy(
      AccountRepository accountRepository,
      AccountStripeRepository accountStripeRepository,
      JournalRepository journalRepository,
      LedgerEntryRepository ledgerEntryRepository,
      OutboxEventRepository outboxEventRepository,
      ObjectMapper objectMapper) {
    this.accountRepository = accountRepository;
    this.accountStripeRepository = accountStripeRepository;
    this.journalRepository = journalRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public StrategyType getType() {
    return StrategyType.S2_STRIPED;
  }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public PostingResult post(JournalRequest request) {
    String scope =
        request.scope() != null && !request.scope().isBlank() ? request.scope() : "global";
    Optional<Journal> existing =
        journalRepository.findByScopeAndIdempotencyKey(scope, request.idempotencyKey());
    if (existing.isPresent()) {
      return new PostingResult(
          existing.get(), ledgerEntryRepository.findByJournalId(existing.get().getId()));
    }

    // Validate legs presence (Invariant I1: At least 2 legs)
    if (request.legs() == null || request.legs().size() < 2) {
      throw new PayCoreException(
          HttpStatus.BAD_REQUEST, "invalid_legs", "Journal must have at least two legs");
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

    // Lock non-hot accounts as usual
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

    // Step 2: Validate non-hot account balances & sufficiency (Invariant I5)
    // Refunds and adjustments are permitted to reduce SELLER_PAYABLE below zero; payouts are not.
    if (request.type() != JournalType.REFUND && request.type() != JournalType.ADJUSTMENT) {
      for (UUID id : nonHotIds) {
        Account account = lockedNonHot.get(id);
        long delta = computeNetDelta(account.getType(), request.legs(), id);
        if (account.getCode().startsWith("MERCHANT_")
            || account.getType() == AccountType.LIABILITY) {
          long prospectiveBalance = account.getBalancePaise() + delta;
          long available = prospectiveBalance - account.getLockedBalancePaise();
          if (available < 0) {
            throw new PayCoreException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "insufficient_funds",
                "Insufficient available balance in account " + account.getCode());
          }
        }
      }
    }

    Journal journal =
        new Journal(
            request.scope(),
            request.idempotencyKey(),
            request.referenceId(),
            request.type(),
            request.description());
    Journal savedJournal = journalRepository.save(journal);

    List<LedgerEntry> entries = new ArrayList<>();
    for (JournalLeg leg : request.legs()) {
      Account account = accountMap.get(leg.accountId());
      long delta =
          (leg.direction() == EntryDirection.DR)
              ? (isDebitNormal(account.getType()) ? leg.amount().paise() : -leg.amount().paise())
              : (isDebitNormal(account.getType()) ? -leg.amount().paise() : leg.amount().paise());

      long runningBalance;
      if (account.isHot() && account.getStripeCount() > 1) {
        // S2: Choose stripe pseudo-randomly across 0 .. stripeCount - 1
        int stripeIndex = Math.abs(ThreadLocalRandom.current().nextInt(account.getStripeCount()));
        AccountStripe stripe =
            accountStripeRepository
                .findStripeForUpdate(account.getId(), stripeIndex)
                .orElseGet(
                    () -> {
                      AccountStripe newStripe =
                          new AccountStripe(new AccountStripeId(account.getId(), stripeIndex), 0L);
                      return accountStripeRepository.save(newStripe);
                    });
        stripe.setBalancePaise(stripe.getBalancePaise() + delta);
        accountStripeRepository.save(stripe);
        runningBalance = stripe.getBalancePaise();
      } else {
        Account nonHot = lockedNonHot.get(account.getId());
        long newBal = nonHot.getBalancePaise() + delta;
        nonHot.setBalancePaise(newBal);
        accountRepository.save(nonHot);
        runningBalance = newBal;
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
              "entriesCount", entries.size(),
              "strategy", "S2_STRIPED");
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
