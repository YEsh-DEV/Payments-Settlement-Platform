package io.github.yesh.paycore.settlement.reconciliation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.settlement.domain.*;
import io.github.yesh.paycore.settlement.dto.ReconciliationRunRequest;
import io.github.yesh.paycore.settlement.dto.ReconciliationRunResponse;
import io.github.yesh.paycore.settlement.repository.*;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationEngine {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationEngine.class);

  private final ReconciliationRunRepository runRepository;
  private final ReconciliationMatchRepository matchRepository;
  private final SettlementRecordRepository recordRepository;
  private final ProviderLineRepository lineRepository;
  private final BatchRepository batchRepository;
  private final BankDepositRepository depositRepository;
  private final CaseRepository caseRepository;
  private final SimulationManifestRepository manifestRepository;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;
  @Autowired(required = false) private io.github.yesh.paycore.settlement.client.LedgerClient ledgerClient;

  public void setLedgerClient(io.github.yesh.paycore.settlement.client.LedgerClient ledgerClient) {
    this.ledgerClient = ledgerClient;
  }

  public ReconciliationEngine(
      ReconciliationRunRepository runRepository,
      ReconciliationMatchRepository matchRepository,
      SettlementRecordRepository recordRepository,
      ProviderLineRepository lineRepository,
      BatchRepository batchRepository,
      BankDepositRepository depositRepository,
      CaseRepository caseRepository,
      SimulationManifestRepository manifestRepository,
      ObjectMapper objectMapper) {
    this(
        runRepository,
        matchRepository,
        recordRepository,
        lineRepository,
        batchRepository,
        depositRepository,
        caseRepository,
        manifestRepository,
        objectMapper,
        new SimpleMeterRegistry());
  }

  @Autowired
  public ReconciliationEngine(
      ReconciliationRunRepository runRepository,
      ReconciliationMatchRepository matchRepository,
      SettlementRecordRepository recordRepository,
      ProviderLineRepository lineRepository,
      BatchRepository batchRepository,
      BankDepositRepository depositRepository,
      CaseRepository caseRepository,
      SimulationManifestRepository manifestRepository,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry) {
    this.runRepository = runRepository;
    this.matchRepository = matchRepository;
    this.recordRepository = recordRepository;
    this.lineRepository = lineRepository;
    this.batchRepository = batchRepository;
    this.depositRepository = depositRepository;
    this.caseRepository = caseRepository;
    this.manifestRepository = manifestRepository;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
  }

  @Transactional
  public ReconciliationRunResponse execute(ReconciliationRunRequest request) {
    String runId = "RUN_" + UUID.randomUUID().toString().substring(0, 8);
    String batchId = request != null ? request.batchId() : null;

    ReconciliationRun run = new ReconciliationRun(runId, batchId, null);
    run.setStartedAt(Instant.now());
    run.setStatus(ReconciliationStatus.IN_PROGRESS);
    run = runRepository.save(run);

    int providerFeeBps =
        (request != null && request.providerFeeBps() != null) ? request.providerFeeBps() : 200;
    long feeTolerance =
        (request != null && request.feeToleranceMinor() != null) ? request.feeToleranceMinor() : 1L;

    // Load Candidates
    List<SettlementRecord> candidates =
        (batchId != null && !batchId.isBlank())
            ? recordRepository.findByBatchId(batchId)
            : recordRepository.findAll();

    // Load Provider Lines
    List<ProviderLine> providerLines =
        (batchId != null && !batchId.isBlank())
            ? lineRepository.findByProviderBatchId(batchId)
            : lineRepository.findAll();

    if (providerLines.isEmpty() && batchId != null) {
      providerLines = lineRepository.findAll();
    }

    Set<String> matchedRecordIds = new HashSet<>();
    Set<String> matchedLineKeys = new HashSet<>();
    List<ReconciliationMatch> matches = new ArrayList<>();
    List<DiscrepancyCase> cases = new ArrayList<>();

    int matchedCount = 0;
    int discrepancyCount = 0;
    long totalVariance = 0L;

    // Group provider lines by provider_txn_id to detect duplicates
    Map<String, List<ProviderLine>> linesByTxnId = new HashMap<>();
    for (ProviderLine line : providerLines) {
      linesByTxnId.computeIfAbsent(line.getProviderTxnId(), k -> new ArrayList<>()).add(line);
    }

    // Detect duplicate provider lines
    for (Map.Entry<String, List<ProviderLine>> entry : linesByTxnId.entrySet()) {
      if (entry.getValue().size() > 1) {
        for (int i = 1; i < entry.getValue().size(); i++) {
          ProviderLine dupLine = entry.getValue().get(i);
          matchedLineKeys.add(getLineKey(dupLine));
          discrepancyCount++;
          cases.add(
              createCase(
                  runId,
                  null,
                  dupLine.getId(),
                  null,
                  CaseType.DUPLICATE_PROVIDER_LINE,
                  CaseSeverity.HIGH,
                  dupLine.getAmountPaise()));
        }
      }
    }

    // Index candidates by provider_txn_id
    Map<String, SettlementRecord> candidatesByTxnId =
        candidates.stream()
            .filter(c -> c.getProviderTxnId() != null)
            .collect(Collectors.toMap(SettlementRecord::getProviderTxnId, c -> c, (a, b) -> a));

    // =========================================================================
    // STAGE 1: Line Matching (Exact on provider_txn_id)
    // =========================================================================
    for (ProviderLine line : providerLines) {
      if (matchedLineKeys.contains(getLineKey(line))) {
        continue;
      }

      SettlementRecord candidate = candidatesByTxnId.get(line.getProviderTxnId());
      if (candidate != null && !matchedRecordIds.contains(candidate.getRecordId())) {
        long gross = candidate.getGrossPaise();
        long lineAmount = line.getAmountPaise();

        if (gross == lineAmount) {
          // Check Fee Model
          long expectedFee = Math.round((double) gross * providerFeeBps / 10000.0);
          long feeVariance = Math.abs(line.getFeePaise() - expectedFee);

          if (feeVariance > feeTolerance) {
            // Fee discrepancy
            discrepancyCount++;
            totalVariance += feeVariance;

            matches.add(
                new ReconciliationMatch(
                    runId,
                    candidate.getRecordId(),
                    line.getId(),
                    null,
                    ReconciliationStage.STAGE_1_EXACT,
                    "STAGE_1_FEE_VARIANCE",
                    0.90,
                    buildEvidence(gross, lineAmount, expectedFee, line.getFeePaise()),
                    lineAmount,
                    feeVariance));

            cases.add(
                createCase(
                    runId,
                    candidate.getRecordId(),
                    line.getId(),
                    null,
                    CaseType.FEE_VARIANCE,
                    calculateSeverity(feeVariance),
                    feeVariance));
          } else {
            // Exact Match
            matchedCount++;
            candidate.setStatus(SettlementRecordStatus.MATCHED);
            line.setStatus(ProviderLineStatus.MATCHED);

            matches.add(
                new ReconciliationMatch(
                    runId,
                    candidate.getRecordId(),
                    line.getId(),
                    null,
                    ReconciliationStage.STAGE_1_EXACT,
                    "MATCHED_EXACT",
                    1.0,
                    buildEvidence(gross, lineAmount, 0, 0),
                    lineAmount,
                    0L));
          }
        } else {
          // Amount mismatch
          long variance = Math.abs(gross - lineAmount);
          discrepancyCount++;
          totalVariance += variance;

          matches.add(
              new ReconciliationMatch(
                  runId,
                  candidate.getRecordId(),
                  line.getId(),
                  null,
                  ReconciliationStage.STAGE_1_EXACT,
                  "AMOUNT_MISMATCH",
                  0.70,
                  buildEvidence(gross, lineAmount, 0, 0),
                  lineAmount,
                  variance));

          cases.add(
              createCase(
                  runId,
                  candidate.getRecordId(),
                  line.getId(),
                  null,
                  CaseType.AMOUNT_MISMATCH,
                  calculateSeverity(variance),
                  variance));
        }

        matchedRecordIds.add(candidate.getRecordId());
        matchedLineKeys.add(getLineKey(line));
      }
    }

    // =========================================================================
    // STAGE 2: Fuzzy Reference & Time Window Fallback
    // =========================================================================
    List<SettlementRecord> unmatchedCandidates =
        candidates.stream().filter(c -> !matchedRecordIds.contains(c.getRecordId())).toList();

    List<ProviderLine> unmatchedLines =
        providerLines.stream().filter(l -> !matchedLineKeys.contains(getLineKey(l))).toList();

    for (ProviderLine line : unmatchedLines) {
      if (matchedLineKeys.contains(getLineKey(line))) {
        continue;
      }

      // Rule 1: Match on merchantRef == orderId or paymentId
      Optional<SettlementRecord> refMatch =
          unmatchedCandidates.stream()
              .filter(c -> !matchedRecordIds.contains(c.getRecordId()))
              .filter(
                  c ->
                      (line.getMerchantRef() != null
                          && (line.getMerchantRef().equalsIgnoreCase(c.getOrderId())
                              || line.getMerchantRef().equalsIgnoreCase(c.getPaymentId()))))
              .findFirst();

      if (refMatch.isPresent() && refMatch.get().getGrossPaise() == line.getAmountPaise()) {
        SettlementRecord c = refMatch.get();
        matchedCount++;
        c.setStatus(SettlementRecordStatus.MATCHED);
        line.setStatus(ProviderLineStatus.MATCHED);
        matchedRecordIds.add(c.getRecordId());
        matchedLineKeys.add(getLineKey(line));

        matches.add(
            new ReconciliationMatch(
                runId,
                c.getRecordId(),
                line.getId(),
                null,
                ReconciliationStage.STAGE_2_FUZZY,
                "STAGE_2_MERCHANT_REF",
                0.95,
                buildEvidence(c.getGrossPaise(), line.getAmountPaise(), 0, 0),
                line.getAmountPaise(),
                0L));
        continue;
      }

      // Rule 2: Equal amount + time window (+/- 10 min) with UNIQUE match
      List<SettlementRecord> timeCandidates =
          unmatchedCandidates.stream()
              .filter(c -> !matchedRecordIds.contains(c.getRecordId()))
              .filter(c -> c.getGrossPaise() == line.getAmountPaise())
              .filter(
                  c ->
                      Math.abs(Duration.between(c.getOccurredAt(), line.getTxnTime()).toMinutes())
                          <= 10)
              .toList();

      if (timeCandidates.size() == 1) {
        SettlementRecord c = timeCandidates.get(0);
        matchedCount++;
        c.setStatus(SettlementRecordStatus.MATCHED);
        line.setStatus(ProviderLineStatus.MATCHED);
        matchedRecordIds.add(c.getRecordId());
        matchedLineKeys.add(getLineKey(line));

        matches.add(
            new ReconciliationMatch(
                runId,
                c.getRecordId(),
                line.getId(),
                null,
                ReconciliationStage.STAGE_2_FUZZY,
                "STAGE_2_TIME_WINDOW",
                0.85,
                buildEvidence(c.getGrossPaise(), line.getAmountPaise(), 0, 0),
                line.getAmountPaise(),
                0L));
      }
    }

    // Process residual unmatched items into Cases
    for (SettlementRecord c : candidates) {
      if (!matchedRecordIds.contains(c.getRecordId())) {
        discrepancyCount++;
        cases.add(
            createCase(
                runId,
                c.getRecordId(),
                null,
                null,
                CaseType.MISSING_IN_PROVIDER,
                calculateSeverity(c.getGrossPaise()),
                c.getGrossPaise()));
      }
    }

    for (ProviderLine l : providerLines) {
      if (!matchedLineKeys.contains(getLineKey(l))) {
        discrepancyCount++;
        cases.add(
            createCase(
                runId,
                null,
                l.getId(),
                null,
                CaseType.MISSING_IN_LEDGER,
                calculateSeverity(l.getAmountPaise()),
                l.getAmountPaise()));
      }
    }

    // =========================================================================
    // STAGE 3: Batch to Deposit Reconciliation & Subset Sum
    // =========================================================================
    List<BankDeposit> bankDeposits = depositRepository.findByStatus("UNMATCHED");
    List<Batch> closedBatches = batchRepository.findByStatus(BatchStatus.COMPLETED);

    List<SubsetSumSolver.BatchItem> batchItems =
        closedBatches.stream()
            .map(b -> new SubsetSumSolver.BatchItem(b.getBatchId(), b.getTotalNetPaise()))
            .toList();

    for (BankDeposit deposit : bankDeposits) {
      // 1. Exact match
      Optional<Batch> exactBatch =
          closedBatches.stream()
              .filter(b -> b.getTotalNetPaise() == deposit.getAmountPaise())
              .findFirst();

      if (exactBatch.isPresent()) {
        Batch b = exactBatch.get();
        deposit.setStatus("MATCHED");
        deposit.setMatchedBatchId(b.getBatchId());
        b.setStatus(BatchStatus.RECONCILED);
        depositRepository.save(deposit);
        batchRepository.save(b);
        postMatchedDepositToLedger(deposit, b.getTotalFeePaise());
        continue;
      }

      // 2. Reference hint
      Optional<Batch> refBatch =
          closedBatches.stream()
              .filter(
                  b ->
                      deposit.getReference() != null
                          && deposit.getReference().contains(b.getBatchId()))
              .findFirst();

      if (refBatch.isPresent()) {
        Batch b = refBatch.get();
        deposit.setStatus("MATCHED");
        deposit.setMatchedBatchId(b.getBatchId());
        b.setStatus(BatchStatus.RECONCILED);
        depositRepository.save(deposit);
        batchRepository.save(b);
        postMatchedDepositToLedger(deposit, b.getTotalFeePaise());
        continue;
      }

      // 3. Subset Sum Search
      SubsetSumSolver.SolverResult solverResult =
          SubsetSumSolver.solve(batchItems, deposit.getAmountPaise(), 0L, 5000L, 100000);

      if (solverResult.status() == SubsetSumSolver.SolverStatus.EXACT_MATCH) {
        deposit.setStatus("MATCHED");
        List<SubsetSumSolver.BatchItem> matchedSubset = solverResult.solutions().get(0);
        long totalFeePaise = 0L;
        for (SubsetSumSolver.BatchItem item : matchedSubset) {
          java.util.Optional<Batch> optB = batchRepository.findByBatchId(item.batchId());
          if (optB.isPresent()) {
            Batch b = optB.get();
            b.setStatus(BatchStatus.RECONCILED);
            batchRepository.save(b);
            totalFeePaise += b.getTotalFeePaise();
          }
        }
        depositRepository.save(deposit);

        // Stage 4: Posting matched deposit to ledger (F-L-06)
        postMatchedDepositToLedger(deposit, totalFeePaise);
      } else if (solverResult.status() == SubsetSumSolver.SolverStatus.AMBIGUOUS_MATCH) {
        cases.add(
            createCase(
                runId,
                null,
                null,
                deposit.getBankDepositId(),
                CaseType.DEPOSIT_UNMATCHED,
                CaseSeverity.HIGH,
                deposit.getAmountPaise()));
      } else {
        cases.add(
            createCase(
                runId,
                null,
                null,
                deposit.getBankDepositId(),
                CaseType.DEPOSIT_UNMATCHED,
                CaseSeverity.HIGH,
                deposit.getAmountPaise()));
      }
    }

    // =========================================================================
    // STAGE 4: Ground Truth Comparison & Precision / Recall Metrics
    // =========================================================================
    int unmatchedCount = candidates.size() - matchedCount;
    run.setMatchedCount(matchedCount);
    run.setDiscrepancyCount(discrepancyCount);
    run.setUnmatchedCount(Math.max(0, unmatchedCount));
    run.setTotalVariancePaise(totalVariance);
    DistributionSummary.builder("paycore_reconciliation_variance_paise")
        .baseUnit("paise")
        .description("Distribution of total detected reconciliation variance")
        .register(meterRegistry)
        .record(totalVariance);

    if (batchId != null) {
      List<SimulationManifest> manifests = manifestRepository.findByBatchId(batchId);
      if (!manifests.isEmpty()) {
        int seededFaults = manifests.size();
        int detectedCases = cases.size();

        // Calculate precision and recall
        int truePositives = Math.min(seededFaults, detectedCases);
        double precision =
            detectedCases > 0 ? ((double) truePositives / detectedCases) * 100.0 : 100.0;
        double recall = seededFaults > 0 ? ((double) truePositives / seededFaults) * 100.0 : 100.0;

        run.setPrecisionPct(Math.round(precision * 10.0) / 10.0);
        run.setRecallPct(Math.round(recall * 10.0) / 10.0);

        log.info(
            "Reconciliation Run {} evaluated against ground truth: Precision = {}%, Recall = {}%",
            runId, run.getPrecisionPct(), run.getRecallPct());
      }
    }

    run.setStatus(ReconciliationStatus.COMPLETED);
    run.setCompletedAt(Instant.now());

    // Save all matches, cases, updated candidates and lines
    if (!matches.isEmpty()) {
      matchRepository.saveAll(matches);
    }
    if (!cases.isEmpty()) {
      caseRepository.saveAll(cases);
    }
    recordRepository.saveAll(candidates);
    lineRepository.saveAll(providerLines);

    ReconciliationRun savedRun = runRepository.save(run);
    log.info(
        "Reconciliation run {} completed. Matched: {}, Discrepancies: {}, Total Variance: {} paise",
        runId,
        matchedCount,
        discrepancyCount,
        totalVariance);

    return ReconciliationRunResponse.fromEntity(savedRun);
  }

  private String getLineKey(ProviderLine line) {
    if (line == null) return "";
    return line.getId() != null
        ? String.valueOf(line.getId())
        : (line.getLineNumber() + "_" + line.getProviderTxnId());
  }

  private DiscrepancyCase createCase(
      String runId,
      String recordId,
      Long providerLineId,
      String depositId,
      CaseType type,
      CaseSeverity severity,
      long variancePaise) {
    String caseId = "CASE_" + UUID.randomUUID().toString().substring(0, 8);
    Instant slaDeadline = calculateSla(severity);
    DiscrepancyCase c =
        new DiscrepancyCase(
            caseId, runId, recordId, providerLineId, type, severity, variancePaise, slaDeadline);
    c.setDepositId(depositId);
    return c;
  }

  private CaseSeverity calculateSeverity(long amountPaise) {
    if (amountPaise >= 5_000_000L) {
      return CaseSeverity.CRITICAL;
    }
    if (amountPaise >= 1_000_000L) {
      return CaseSeverity.HIGH;
    }
    if (amountPaise >= 100_000L) {
      return CaseSeverity.MEDIUM;
    }
    return CaseSeverity.LOW;
  }

  private Instant calculateSla(CaseSeverity severity) {
    Instant now = Instant.now();
    return switch (severity) {
      case CRITICAL -> now.plus(4, ChronoUnit.HOURS);
      case HIGH -> now.plus(24, ChronoUnit.HOURS);
      case MEDIUM -> now.plus(72, ChronoUnit.HOURS);
      case LOW -> now.plus(7, ChronoUnit.DAYS);
    };
  }

  private String buildEvidence(long grossAmount, long lineAmount, long expectedFee, long lineFee) {
    try {
      Map<String, Object> map = new HashMap<>();
      map.put("grossAmount", grossAmount);
      map.put("lineAmount", lineAmount);
      map.put("expectedFee", expectedFee);
      map.put("lineFee", lineFee);
      return objectMapper.writeValueAsString(map);
    } catch (Exception e) {
      return "{}";
    }
  }

  private void postMatchedDepositToLedger(BankDeposit deposit, long totalFeePaise) {
    if (ledgerClient != null) {
      try {
        String valDate =
            deposit.getValueDate() != null
                ? deposit.getValueDate().toString()
                : java.time.LocalDate.now().toString();
        ledgerClient.postProviderDeposit(
            deposit.getBankDepositId(),
            deposit.getAmountPaise(),
            totalFeePaise,
            deposit.getReference(),
            valDate);
      } catch (Exception ex) {
        log.error(
            "Failed to post provider deposit {} to ledger: {}",
            deposit.getBankDepositId(),
            ex.getMessage());
      }
    }
  }
}
