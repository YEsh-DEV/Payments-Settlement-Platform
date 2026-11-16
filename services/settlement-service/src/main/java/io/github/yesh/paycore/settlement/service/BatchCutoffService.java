package io.github.yesh.paycore.settlement.service;

import io.github.yesh.paycore.settlement.client.LedgerClient;
import io.github.yesh.paycore.settlement.domain.Batch;
import io.github.yesh.paycore.settlement.domain.BatchStatus;
import io.github.yesh.paycore.settlement.domain.SettlementPayout;
import io.github.yesh.paycore.settlement.domain.SettlementRecord;
import io.github.yesh.paycore.settlement.domain.SettlementRecordStatus;
import io.github.yesh.paycore.settlement.dto.BatchCutoffRequest;
import io.github.yesh.paycore.settlement.dto.BatchResponse;
import io.github.yesh.paycore.settlement.exception.BatchNotFoundException;
import io.github.yesh.paycore.settlement.exception.InvalidBatchStateException;
import io.github.yesh.paycore.settlement.exception.InvalidTransitionException;
import io.github.yesh.paycore.settlement.repository.BatchRepository;
import io.github.yesh.paycore.settlement.repository.SettlementPayoutRepository;
import io.github.yesh.paycore.settlement.repository.SettlementRecordRepository;
import java.io.BufferedReader;
import java.io.StringReader;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchCutoffService {

  private static final Logger log = LoggerFactory.getLogger(BatchCutoffService.class);
  private static final long ADVISORY_LOCK_ID = 8493021L;

  private final BatchRepository batchRepository;
  private final SettlementRecordRepository settlementRecordRepository;
  private final SettlementPayoutRepository settlementPayoutRepository;
  private final LedgerClient ledgerClient;
  private final JdbcTemplate jdbcTemplate;

  @Autowired
  public BatchCutoffService(
      BatchRepository batchRepository,
      SettlementRecordRepository settlementRecordRepository,
      SettlementPayoutRepository settlementPayoutRepository,
      @Autowired(required = false) LedgerClient ledgerClient,
      @Autowired(required = false) JdbcTemplate jdbcTemplate) {
    this.batchRepository = batchRepository;
    this.settlementRecordRepository = settlementRecordRepository;
    this.settlementPayoutRepository = settlementPayoutRepository;
    this.ledgerClient = ledgerClient;
    this.jdbcTemplate = jdbcTemplate;
  }

  // Testing constructor without remote client or jdbcTemplate
  public BatchCutoffService(
      BatchRepository batchRepository,
      SettlementRecordRepository settlementRecordRepository,
      SettlementPayoutRepository settlementPayoutRepository) {
    this(batchRepository, settlementRecordRepository, settlementPayoutRepository, null, null);
  }

  @Transactional
  public BatchResponse runCutoff(BatchCutoffRequest request) {
    acquireAdvisoryLock();

    Instant cutoffTime =
        (request != null && request.cutoffTime() != null) ? request.cutoffTime() : Instant.now();

    long minPayoutAmount =
        (request != null && request.minPayoutAmountPaise() != null)
            ? request.minPayoutAmountPaise()
            : 10000L; // Rs 100 default

    Long maxPayoutAmount = (request != null) ? request.maxPayoutAmountPaise() : null;

    List<SettlementRecord> pending =
        settlementRecordRepository.findByBatchIdIsNullAndOccurredAtLessThanEqual(cutoffTime);

    String batchId =
        "BATCH_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);
    String cutoffWindow = DateTimeFormatter.ISO_INSTANT.format(cutoffTime);

    Batch batch = new Batch(batchId, cutoffWindow);

    if (pending.isEmpty()) {
      batch.setStatus(BatchStatus.CLOSED);
      batch.setClosedAt(Instant.now());
      batchRepository.save(batch);
      log.info("Cutoff executed for batch {}. No pending records found.", batchId);
      return BatchResponse.fromEntity(batch);
    }

    int count = pending.size();
    long totalGross = 0L;
    long totalFee = 0L;
    long totalNet = 0L;

    Map<String, Long> sellerTotals = new HashMap<>();

    for (SettlementRecord rec : pending) {
      rec.setBatchId(batchId);
      rec.setStatus(SettlementRecordStatus.BATCHED);
      totalGross += rec.getGrossPaise();
      totalFee += rec.getFeePaise();
      totalNet += rec.getNetPaise();

      sellerTotals.merge(rec.getSellerId(), rec.getNetPaise(), Long::sum);
    }

    batch.setTotalCount(count);
    batch.setTotalGrossPaise(totalGross);
    batch.setTotalFeePaise(totalFee);
    batch.setTotalNetPaise(totalNet);
    batch.setStatus(BatchStatus.CREATED);

    batchRepository.save(batch);
    settlementRecordRepository.saveAll(pending);

    // Create payouts per seller with deterministic IDs batchId:sellerId
    List<SettlementPayout> payouts = new ArrayList<>();
    for (Map.Entry<String, Long> entry : sellerTotals.entrySet()) {
      String sellerId = entry.getKey();
      long netAmount = entry.getValue();

      if (netAmount >= minPayoutAmount) {
        if (maxPayoutAmount != null && maxPayoutAmount > 0) {
          netAmount = Math.min(netAmount, maxPayoutAmount);
        }
        String payoutId = batchId + ":" + sellerId;
        SettlementPayout payout = new SettlementPayout(payoutId, batchId, sellerId, netAmount);
        payouts.add(payout);
      }
    }

    if (!payouts.isEmpty()) {
      settlementPayoutRepository.saveAll(payouts);
      batch.setStatus(BatchStatus.PAYOUTS_REQUESTED);
      batchRepository.save(batch);
      log.info("Generated {} payouts for batch {}", payouts.size(), batchId);

      // Call ledger POST /v1/payouts with idempotency key equal to payout id
      boolean anyFailed = false;
      for (SettlementPayout p : payouts) {
        if (ledgerClient != null) {
          try {
            Map<String, Object> resp =
                ledgerClient.initiatePayout(p.getPayoutId(), p.getSellerId(), p.getAmountPaise());
            p.setLedgerPayoutId(p.getPayoutId());
            settlementPayoutRepository.save(p);
          } catch (Exception ex) {
            log.error(
                "Failed to initiate ledger payout for payoutId {}: {}",
                p.getPayoutId(),
                ex.getMessage());
            anyFailed = true;
          }
        }
      }

      if (anyFailed) {
        batch.setStatus(BatchStatus.PARTIAL_FAILURE);
        batchRepository.save(batch);
      }
    } else {
      batch.setStatus(BatchStatus.COMPLETED);
      batch.setClosedAt(Instant.now());
      batchRepository.save(batch);
    }

    log.info(
        "Cutoff batch {} transitioned to {} with {} records, net: {} paise",
        batchId,
        batch.getStatus(),
        count,
        totalNet);

    return BatchResponse.fromEntity(batch);
  }

  @Transactional
  public String sendPayoutFile(String batchId) {
    Batch batch =
        batchRepository
            .findByBatchId(batchId)
            .orElseThrow(() -> new BatchNotFoundException("Batch not found: " + batchId));

    if (batch.getStatus() == BatchStatus.SENT) {
      log.info("Batch {} already marked SENT, returning file", batchId);
      return generateBankPayoutFileContent(batchId);
    }

    if (batch.getStatus() != BatchStatus.PAYOUTS_REQUESTED
        && batch.getStatus() != BatchStatus.CREATED
        && batch.getStatus() != BatchStatus.PARTIAL_FAILURE) {
      throw new InvalidTransitionException(
          "Cannot send payout file for batch " + batchId + " in status " + batch.getStatus());
    }

    List<SettlementPayout> payouts = settlementPayoutRepository.findByBatchId(batchId);
    for (SettlementPayout p : payouts) {
      if ("REQUESTED".equalsIgnoreCase(p.getStatus())) {
        p.setStatus("SENT");
      }
    }
    settlementPayoutRepository.saveAll(payouts);

    batch.setStatus(BatchStatus.SENT);
    batchRepository.save(batch);

    return generateBankPayoutFileContent(batchId);
  }

  @Transactional
  public BatchResponse processBankResults(String batchId, String bankResultCsv) {
    Batch batch =
        batchRepository
            .findByBatchId(batchId)
            .orElseThrow(() -> new BatchNotFoundException("Batch not found: " + batchId));

    if (batch.getStatus() != BatchStatus.SENT && batch.getStatus() != BatchStatus.PARTIAL_FAILURE) {
      throw new InvalidTransitionException(
          "Cannot process bank results for batch " + batchId + " in status " + batch.getStatus());
    }

    List<SettlementPayout> payouts = settlementPayoutRepository.findByBatchId(batchId);
    Map<String, SettlementPayout> payoutMap = new HashMap<>();
    for (SettlementPayout p : payouts) {
      payoutMap.put(p.getPayoutId(), p);
    }

    boolean anyFailure = false;

    try (BufferedReader reader = new BufferedReader(new StringReader(bankResultCsv))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("payout_id") || line.startsWith("#")) {
          continue;
        }

        String[] parts = line.split(",");
        if (parts.length >= 2) {
          String payoutId = parts[0].trim();
          String status = parts[1].trim().toUpperCase();
          String reason = parts.length > 2 ? parts[2].trim() : "Bank result processed";

          SettlementPayout p = payoutMap.get(payoutId);
          if (p == null) {
            log.warn("Payout {} not found in batch {}", payoutId, batchId);
            continue;
          }

          if ("CONFIRMED".equalsIgnoreCase(status)
              || "PAID".equalsIgnoreCase(status)
              || "SUCCESS".equalsIgnoreCase(status)) {
            if ("FAILED".equalsIgnoreCase(p.getStatus())) {
              throw new InvalidTransitionException(
                  "Cannot confirm payout " + payoutId + " in FAILED status");
            }
            if ("CONFIRMED".equalsIgnoreCase(p.getStatus())) {
              continue; // idempotent
            }

            if (ledgerClient != null) {
              ledgerClient.confirmPayout(payoutId, batchId);
            }
            p.setStatus("CONFIRMED");
          } else {
            if ("CONFIRMED".equalsIgnoreCase(p.getStatus())) {
              throw new InvalidTransitionException(
                  "Cannot fail payout " + payoutId + " in CONFIRMED status");
            }
            if ("FAILED".equalsIgnoreCase(p.getStatus())) {
              continue; // idempotent
            }

            if (ledgerClient != null) {
              ledgerClient.failPayout(payoutId, reason);
            }
            p.setStatus("FAILED");
            anyFailure = true;
          }
          settlementPayoutRepository.save(p);
        }
      }
    } catch (InvalidTransitionException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse bank results CSV: " + e.getMessage(), e);
    }

    if (anyFailure) {
      batch.setStatus(BatchStatus.PARTIAL_FAILURE);
    } else {
      batch.setStatus(BatchStatus.COMPLETED);
    }
    batch.setClosedAt(Instant.now());
    Batch saved = batchRepository.save(batch);

    return BatchResponse.fromEntity(saved);
  }

  @Transactional
  public BatchResponse resumeBatch(String batchId) {
    Batch batch =
        batchRepository
            .findByBatchId(batchId)
            .orElseThrow(() -> new BatchNotFoundException("Batch not found: " + batchId));

    List<SettlementPayout> payouts = settlementPayoutRepository.findByBatchId(batchId);
    boolean anyFailed = false;

    for (SettlementPayout p : payouts) {
      if ("REQUESTED".equalsIgnoreCase(p.getStatus()) && p.getLedgerPayoutId() == null) {
        if (ledgerClient != null) {
          try {
            ledgerClient.initiatePayout(p.getPayoutId(), p.getSellerId(), p.getAmountPaise());
            p.setLedgerPayoutId(p.getPayoutId());
            settlementPayoutRepository.save(p);
          } catch (Exception e) {
            log.error("Failed to resume payout {}: {}", p.getPayoutId(), e.getMessage());
            anyFailed = true;
          }
        }
      }
    }

    if (!anyFailed) {
      batch.setStatus(BatchStatus.PAYOUTS_REQUESTED);
    } else {
      batch.setStatus(BatchStatus.PARTIAL_FAILURE);
    }
    Batch saved = batchRepository.save(batch);
    return BatchResponse.fromEntity(saved);
  }

  public String generateBankPayoutFileContent(String batchId) {
    List<SettlementPayout> payouts = settlementPayoutRepository.findByBatchId(batchId);
    StringBuilder sb = new StringBuilder();
    sb.append("payout_id,seller_id,amount,reference\n");
    for (SettlementPayout p : payouts) {
      sb.append(p.getPayoutId())
          .append(",")
          .append(p.getSellerId())
          .append(",")
          .append(p.getAmountPaise())
          .append(",")
          .append("PAYCORE_PAYOUT_")
          .append(batchId)
          .append("\n");
    }
    return sb.toString();
  }

  private void acquireAdvisoryLock() {
    if (jdbcTemplate != null) {
      try {
        Boolean acquired =
            jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, ADVISORY_LOCK_ID);
        if (Boolean.FALSE.equals(acquired)) {
          throw new InvalidBatchStateException(
              "Another batch cutoff run is currently in progress (advisory lock busy)");
        }
      } catch (BadSqlGrammarException ignored) {
        // Ignored for non-Postgres test DBs
      }
    }
  }

  @Transactional(readOnly = true)
  public BatchResponse getBatch(String batchId) {
    return batchRepository
        .findByBatchId(batchId)
        .map(BatchResponse::fromEntity)
        .orElseThrow(() -> new BatchNotFoundException("Batch not found: " + batchId));
  }

  @Transactional(readOnly = true)
  public Page<BatchResponse> listBatches(Pageable pageable) {
    return batchRepository.findAllByOrderByCreatedAtDesc(pageable).map(BatchResponse::fromEntity);
  }

  @Transactional(readOnly = true)
  public Page<SettlementRecord> getBatchRecords(String batchId, Pageable pageable) {
    return settlementRecordRepository.findByBatchId(batchId, pageable);
  }

  @Scheduled(cron = "${paycore.settlement.cutoff-cron:0 0 * * * *}")
  public void scheduledCutoff() {
    log.info("Running scheduled batch cutoff...");
    try {
      runCutoff(new BatchCutoffRequest(Instant.now(), 10000L, null));
    } catch (Exception e) {
      log.error("Scheduled batch cutoff failed", e);
    }
  }
}
