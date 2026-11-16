package io.github.yesh.paycore.ledger.service;

import io.github.yesh.paycore.ledger.domain.*;
import io.github.yesh.paycore.ledger.dto.IntegrityReportResponse;
import io.github.yesh.paycore.ledger.dto.IntegrityViolation;
import io.github.yesh.paycore.ledger.repository.*;
import io.github.yesh.paycore.ledger.strategy.PostingStrategyRouter;
import io.github.yesh.paycore.ledger.strategy.StrategyType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Continuous Ledger Integrity Verification Service (F-L-09). Verifies Invariants I1 (double-entry
 * balance), I3 (global zero-sum), and I4 (cached balance vs entry ledger). Exposes GET
 * /v1/integrity and runs a scheduled nightly audit.
 */
@Service
public class IntegrityService {

  private static final Logger log = LoggerFactory.getLogger(IntegrityService.class);

  private final AccountRepository accountRepository;
  private final AccountStripeRepository accountStripeRepository;
  private final AccountSnapshotRepository accountSnapshotRepository;
  private final JournalRepository journalRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final PostingStrategyRouter strategyRouter;
  private final Counter integrityViolationCounter;

  @Autowired
  public IntegrityService(
      AccountRepository accountRepository,
      AccountStripeRepository accountStripeRepository,
      AccountSnapshotRepository accountSnapshotRepository,
      JournalRepository journalRepository,
      LedgerEntryRepository ledgerEntryRepository,
      PostingStrategyRouter strategyRouter,
      MeterRegistry meterRegistry) {
    this.accountRepository = accountRepository;
    this.accountStripeRepository = accountStripeRepository;
    this.accountSnapshotRepository = accountSnapshotRepository;
    this.journalRepository = journalRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.strategyRouter = strategyRouter;
    this.integrityViolationCounter =
        Counter.builder("ledger_integrity_violations_total")
            .description("Total number of ledger integrity invariant violations detected")
            .register(meterRegistry);
  }

  public IntegrityService(
      AccountRepository accountRepository,
      AccountStripeRepository accountStripeRepository,
      AccountSnapshotRepository accountSnapshotRepository,
      JournalRepository journalRepository,
      LedgerEntryRepository ledgerEntryRepository,
      PostingStrategyRouter strategyRouter) {
    this(
        accountRepository,
        accountStripeRepository,
        accountSnapshotRepository,
        journalRepository,
        ledgerEntryRepository,
        strategyRouter,
        new SimpleMeterRegistry());
  }

  @Transactional(readOnly = true)
  public IntegrityReportResponse runIntegrityCheck() {
    List<IntegrityViolation> violations = new ArrayList<>();
    StrategyType activeStrategy = strategyRouter.getCurrentStrategy();

    // 1. Invariant I3: Globally, total debits equal total credits
    Long totalDebits = ledgerEntryRepository.sumTotalByDirection(EntryDirection.DR);
    Long totalCredits = ledgerEntryRepository.sumTotalByDirection(EntryDirection.CR);
    if (totalDebits == null) totalDebits = 0L;
    if (totalCredits == null) totalCredits = 0L;

    long delta = totalDebits - totalCredits;
    if (delta != 0L) {
      violations.add(
          new IntegrityViolation(
              "I3",
              "GLOBAL_LEDGER",
              "SYSTEM",
              "Global debits ("
                  + totalDebits
                  + ") do not equal global credits ("
                  + totalCredits
                  + ")",
              totalDebits,
              totalCredits));
    }

    // 2. Invariant I4: Cached balance = sum of entries (striped: sum of stripes; S4: snapshot +
    // delta)
    List<Account> allAccounts = accountRepository.findAll();
    for (Account account : allAccounts) {
      long calculatedBalanceFromEntries =
          calculateAccountBalanceFromEntries(account.getId(), account.getType());

      if (account.isHot() && activeStrategy == StrategyType.S2_STRIPED) {
        long stripeSum = accountStripeRepository.sumBalanceByAccountId(account.getId());
        if (stripeSum != calculatedBalanceFromEntries) {
          violations.add(
              new IntegrityViolation(
                  "I4",
                  "ACCOUNT_STRIPES",
                  account.getCode(),
                  "Striped account sum ("
                      + stripeSum
                      + ") does not match entry sum ("
                      + calculatedBalanceFromEntries
                      + ")",
                  calculatedBalanceFromEntries,
                  stripeSum));
        }
      } else if (account.isHot() && activeStrategy == StrategyType.S4_LEDGER_ONLY) {
        Optional<AccountSnapshot> snapshotOpt =
            accountSnapshotRepository.findFirstByAccountIdOrderByAsOfEntryIdDesc(account.getId());
        long snapshotBal = snapshotOpt.map(AccountSnapshot::getBalancePaise).orElse(0L);
        long asOfEntryId = snapshotOpt.map(AccountSnapshot::getAsOfEntryId).orElse(0L);
        long deltaFromEntries =
            calculateDeltaAfterEntryId(account.getId(), account.getType(), asOfEntryId);
        long effectiveBal = snapshotBal + deltaFromEntries;
        if (effectiveBal != calculatedBalanceFromEntries) {
          violations.add(
              new IntegrityViolation(
                  "I4",
                  "ACCOUNT_SNAPSHOT_DELTA",
                  account.getCode(),
                  "S4 snapshot+delta ("
                      + effectiveBal
                      + ") does not match entry sum ("
                      + calculatedBalanceFromEntries
                      + ")",
                  calculatedBalanceFromEntries,
                  effectiveBal));
        }
      } else {
        if (account.getBalancePaise() != calculatedBalanceFromEntries) {
          violations.add(
              new IntegrityViolation(
                  "I4",
                  "ACCOUNT_CACHED_BALANCE",
                  account.getCode(),
                  "Account cached balance ("
                      + account.getBalancePaise()
                      + ") does not match entry sum ("
                      + calculatedBalanceFromEntries
                      + ")",
                  calculatedBalanceFromEntries,
                  account.getBalancePaise()));
        }
      }
    }

    // 3. Sample Invariant I1: Every journal has at least two lines, positive amounts, and debits ==
    // credits
    List<Journal> journals = journalRepository.findAll();
    for (Journal journal : journals) {
      List<LedgerEntry> entries = ledgerEntryRepository.findByJournalId(journal.getId());
      if (entries.size() < 2) {
        violations.add(
            new IntegrityViolation(
                "I1",
                "JOURNAL",
                journal.getId().toString(),
                "Journal has fewer than 2 entries (" + entries.size() + ")",
                2,
                entries.size()));
        continue;
      }

      long journalDr = 0L;
      long journalCr = 0L;
      boolean nonPositiveFound = false;

      for (LedgerEntry entry : entries) {
        if (entry.getAmountPaise() <= 0) {
          nonPositiveFound = true;
        }
        if (entry.getDirection() == EntryDirection.DR) {
          journalDr += entry.getAmountPaise();
        } else {
          journalCr += entry.getAmountPaise();
        }
      }

      if (nonPositiveFound) {
        violations.add(
            new IntegrityViolation(
                "I1",
                "JOURNAL_LEG_AMOUNT",
                journal.getId().toString(),
                "Journal contains entry with non-positive amount",
                1,
                0));
      }

      if (journalDr != journalCr) {
        violations.add(
            new IntegrityViolation(
                "I1",
                "JOURNAL_BALANCE",
                journal.getId().toString(),
                "Journal debits (" + journalDr + ") do not equal credits (" + journalCr + ")",
                journalDr,
                journalCr));
      }
    }

    if (!violations.isEmpty()) {
      integrityViolationCounter.increment(violations.size());
      log.error("Ledger integrity check failed! Found {} violations", violations.size());
    } else {
      log.info(
          "Ledger integrity check passed cleanly. Checked {} accounts and {} journals.",
          allAccounts.size(),
          journals.size());
    }

    return new IntegrityReportResponse(
        violations.isEmpty(),
        activeStrategy.name(),
        allAccounts.size(),
        journals.size(),
        totalDebits,
        totalCredits,
        delta,
        violations,
        Instant.now());
  }

  @Scheduled(cron = "${paycore.ledger.integrity.cron:0 0 2 * * *}")
  public void scheduledIntegrityCheck() {
    log.info("Running scheduled ledger integrity check");
    runIntegrityCheck();
  }

  private long calculateAccountBalanceFromEntries(UUID accountId, AccountType type) {
    Long drSum =
        ledgerEntryRepository.sumAmountByAccountIdAndDirection(accountId, EntryDirection.DR);
    Long crSum =
        ledgerEntryRepository.sumAmountByAccountIdAndDirection(accountId, EntryDirection.CR);
    long dr = drSum != null ? drSum : 0L;
    long cr = crSum != null ? crSum : 0L;
    boolean debitNormal = (type == AccountType.ASSET || type == AccountType.EXPENSE);
    return debitNormal ? (dr - cr) : (cr - dr);
  }

  private long calculateDeltaAfterEntryId(UUID accountId, AccountType type, long afterEntryId) {
    Long drSum =
        ledgerEntryRepository.sumAmountByAccountIdAndDirectionAndIdGreaterThan(
            accountId, EntryDirection.DR, afterEntryId);
    Long crSum =
        ledgerEntryRepository.sumAmountByAccountIdAndDirectionAndIdGreaterThan(
            accountId, EntryDirection.CR, afterEntryId);
    long dr = drSum != null ? drSum : 0L;
    long cr = crSum != null ? crSum : 0L;
    boolean debitNormal = (type == AccountType.ASSET || type == AccountType.EXPENSE);
    return debitNormal ? (dr - cr) : (cr - dr);
  }
}
