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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BatchCutoffService {

  private static final Logger log = LoggerFactory.getLogger(BatchCutoffService.class);
  private static final long ADVISORY_LOCK_ID = 8493021L;

  private final BatchRepository batchRepository;
  private final SettlementRecordRepository settlementRecordRepository;
  private final SettlementPayoutRepository settlementPayoutRepository;
  private final LedgerClient ledgerClient;
  private final JdbcTemplate jdbcTemplate;
  private final DataSource dataSource;
  private final TransactionTemplate transactionTemplate;

  @Autowired
  public BatchCutoffService(
      BatchRepository batchRepository,
      SettlementRecordRepository settlementRecordRepository,
      SettlementPayoutRepository settlementPayoutRepository,
      @Autowired(required = false) LedgerClient ledgerClient,
      @Autowired(required = false) JdbcTemplate jdbcTemplate,
      @Autowired(required = false) DataSource dataSource,
      @Autowired(required = false) PlatformTransactionManager transactionManager) {
    this.batchRepository = batchRepository;
    this.settlementRecordRepository = settlementRecordRepository;
    this.settlementPayoutRepository = settlementPayoutRepository;
    this.ledgerClient = ledgerClient;
    this.jdbcTemplate = jdbcTemplate;
    this.dataSource =
        dataSource != null
            ? dataSource
            : (jdbcTemplate != null ? jdbcTemplate.getDataSource() : null);
    this.transactionTemplate =
        transactionManager != null ? new TransactionTemplate(transactionManager) : null;
  }

  // Testing constructor without remote client or jdbcTemplate
  public BatchCutoffService(
      BatchRepository batchRepository,
      SettlementRecordRepository settlementRecordRepository,
      SettlementPayoutRepository settlementPayoutRepository) {
    this(
        batchRepository,
        settlementRecordRepository,
        settlementPayoutRepository,
        null,
        null,
        null,
        null);
  }

  // Testing constructor with jdbcTemplate
  public BatchCutoffService(
      BatchRepository batchRepository,
      SettlementRecordRepository settlementRecordRepository,
      SettlementPayoutRepository settlementPayoutRepository,
      LedgerClient ledgerClient,
      JdbcTemplate jdbcTemplate) {
    this(
        batchRepository,
        settlementRecordRepository,
        settlementPayoutRepository,
        ledgerClient,
        jdbcTemplate,
        null,
        null);
  }

  private <T> T executeInTransaction(Supplier<T> action) {
    if (transactionTemplate != null) {
      return transactionTemplate.execute(status -> action.get());
    }
    return action.get();
  }

  private record LockHandle(Connection connection, boolean acquired) {}

  private LockHandle acquireSessionAdvisoryLock() {
    if (dataSource != null) {
      try {
        Connection conn = dataSource.getConnection();
        boolean ok = false;
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
          ps.setLong(1, ADVISORY_LOCK_ID);
          try (ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getBoolean(1)) {
              ok = true;
            }
          }
        }
        if (!ok) {
          try {
            conn.close();
          } catch (Exception ignored) {
          }
          throw new InvalidBatchStateException(
              "Another batch cutoff run is currently in progress (advisory lock busy)");
        }
        return new LockHandle(conn, true);
      } catch (SQLException e) {
        log.warn("Database advisory lock unavailable/unsupported dialect: {}", e.getMessage());
      }
    } else if (jdbcTemplate != null) {
      try {
        Boolean acquired =
            jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(?)", Boolean.class, ADVISORY_LOCK_ID);
        if (Boolean.FALSE.equals(acquired)) {
          throw new InvalidBatchStateException(
              "Another batch cutoff run is currently in progress (advisory lock busy)");
        }
      } catch (BadSqlGrammarException ignored) {
      }
    }
    return new LockHandle(null, false);
  }

  private void releaseSessionAdvisoryLock(LockHandle handle) {
    if (handle != null && handle.connection() != null) {
      if (handle.acquired()) {
        try (PreparedStatement ps =
            handle.connection().prepareStatement("SELECT pg_advisory_unlock(?)")) {
          ps.setLong(1, ADVISORY_LOCK_ID);
          ps.executeQuery();
        } catch (Exception e) {
          log.warn("Failed to release advisory lock explicitly: {}", e.getMessage());
        }
      }
      try {
        handle.connection().close();
      } catch (Exception ignored) {
      }
    }
  }

  public BatchResponse runCutoff(BatchCutoffRequest request) {
    LockHandle lock = acquireSessionAdvisoryLock();
    try {
      Instant cutoffTime =
          (request != null && request.cutoffTime() != null) ? request.cutoffTime() : Instant.now();

      long minPayoutAmount =
          (request != null && request.minPayoutAmountPaise() != null)
              ? request.minPayoutAmountPaise()
              : 10000L; // Rs 100 default

      Long maxPayoutAmount = (request != null) ? request.maxPayoutAmountPaise() : null;

      String batchId =
          "BATCH_"
              + System.currentTimeMillis()
              + "_"
              + UUID.randomUUID().toString().substring(0, 6);
      String cutoffWindow = DateTimeFormatter.ISO_INSTANT.format(cutoffTime);

      // Phase 1: Short transaction to find pending records and stage batch & payout entities
      record InitialBatchState(Batch batch, List<SettlementPayout> payouts, boolean empty) {}

      InitialBatchState initialState =
          executeInTransaction(
              () -> {
                List<SettlementRecord> pending =
                    settlementRecordRepository.findByBatchIdIsNullAndOccurredAtLessThanEqual(
                        cutoffTime);
                Batch b = new Batch(batchId, cutoffWindow);

                if (pending.isEmpty()) {
                  b.setStatus(BatchStatus.CLOSED);
                  b.setClosedAt(Instant.now());
                  b.setTotalCount(0);
                  b.setTotalGrossPaise(0L);
                  b.setTotalFeePaise(0L);
                  b.setTotalNetPaise(0L);
                  batchRepository.save(b);
                  return new InitialBatchState(b, List.of(), true);
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

                b.setTotalCount(count);
                b.setTotalGrossPaise(totalGross);
                b.setTotalFeePaise(totalFee);
                b.setTotalNetPaise(totalNet);
                b.setStatus(BatchStatus.CREATED);

                batchRepository.save(b);
                settlementRecordRepository.saveAll(pending);

                List<SettlementPayout> stagedPayouts = new ArrayList<>();
                for (Map.Entry<String, Long> entry : sellerTotals.entrySet()) {
                  String sellerId = entry.getKey();
                  long netAmount = entry.getValue();
                  if (netAmount >= minPayoutAmount) {
                    if (maxPayoutAmount != null && maxPayoutAmount > 0) {
                      netAmount = Math.min(netAmount, maxPayoutAmount);
                    }
                    String payoutId = batchId + ":" + sellerId;
                    SettlementPayout payout =
                        new SettlementPayout(payoutId, batchId, sellerId, netAmount);
                    payout.setStatus("REQUESTED");
                    stagedPayouts.add(payout);
                  }
                }

                if (!stagedPayouts.isEmpty()) {
                  settlementPayoutRepository.saveAll(stagedPayouts);
                  b.setStatus(BatchStatus.PAYOUTS_REQUESTED);
                  batchRepository.save(b);
                }

                return new InitialBatchState(b, stagedPayouts, false);
              });

      if (initialState.empty()) {
        log.info("Cutoff executed for batch {}. No pending records found.", batchId);
        return BatchResponse.fromEntity(initialState.batch());
      }

      Batch batch = initialState.batch();
      List<SettlementPayout> payouts = initialState.payouts();

      if (payouts.isEmpty()) {
        executeInTransaction(
            () -> {
              Batch fresh = batchRepository.findByBatchId(batchId).orElse(batch);
              fresh.setStatus(BatchStatus.COMPLETED);
              fresh.setClosedAt(Instant.now());
              return batchRepository.save(fresh);
            });
        return BatchResponse.fromEntity(batch);
      }

      // Phase 2: Call ledger HTTP POST /v1/payouts outside DB transaction
      // Each payout's outcome is committed in its own short transaction
      boolean anyFailed = false;
      for (SettlementPayout p : payouts) {
        if (ledgerClient != null) {
          try {
            ledgerClient.initiatePayout(p.getPayoutId(), p.getSellerId(), p.getAmountPaise());
            executeInTransaction(
                () -> {
                  p.setLedgerPayoutId(p.getPayoutId());
                  p.setStatus("REQUESTED");
                  settlementPayoutRepository.save(p);
                  return null;
                });
          } catch (Exception ex) {
            log.error(
                "Failed to initiate ledger payout for payoutId {}: {}",
                p.getPayoutId(),
                ex.getMessage());
            anyFailed = true;
            executeInTransaction(
                () -> {
                  p.setStatus("FAILED");
                  settlementPayoutRepository.save(p);
                  return null;
                });
          }
        }
      }

      // Phase 3: Short transaction to finalize batch status
      final boolean finalAnyFailed = anyFailed;
      Batch finalBatch =
          executeInTransaction(
              () -> {
                Batch fresh = batchRepository.findByBatchId(batchId).orElse(batch);
                if (finalAnyFailed) {
                  fresh.setStatus(BatchStatus.PARTIAL_FAILURE);
                } else {
                  fresh.setStatus(BatchStatus.PAYOUTS_REQUESTED);
                }
                return batchRepository.save(fresh);
              });

      log.info(
          "Cutoff batch {} transitioned to {} with {} records, net: {} paise",
          batchId,
          finalBatch.getStatus(),
          finalBatch.getTotalCount(),
          finalBatch.getTotalNetPaise());

      return BatchResponse.fromEntity(finalBatch);
    } finally {
      releaseSessionAdvisoryLock(lock);
    }
  }

  public BatchResponse resumeBatch(String batchId) {
    LockHandle lock = acquireSessionAdvisoryLock();
    try {
      Batch batch =
          batchRepository
              .findByBatchId(batchId)
              .orElseThrow(() -> new BatchNotFoundException("Batch not found: " + batchId));

      List<SettlementPayout> payouts = settlementPayoutRepository.findByBatchId(batchId);
      boolean anyFailed = false;

      for (SettlementPayout p : payouts) {
        if (p.getLedgerPayoutId() == null || "FAILED".equalsIgnoreCase(p.getStatus())) {
          if (ledgerClient != null) {
            try {
              ledgerClient.initiatePayout(p.getPayoutId(), p.getSellerId(), p.getAmountPaise());
              executeInTransaction(
                  () -> {
                    p.setLedgerPayoutId(p.getPayoutId());
                    p.setStatus("REQUESTED");
                    settlementPayoutRepository.save(p);
                    return null;
                  });
            } catch (Exception ex) {
              log.error(
                  "Failed to initiate ledger payout for payoutId {}: {}",
                  p.getPayoutId(),
                  ex.getMessage());
              anyFailed = true;
              executeInTransaction(
                  () -> {
                    p.setStatus("FAILED");
                    settlementPayoutRepository.save(p);
                    return null;
                  });
            }
          }
        }
      }

      final boolean failed = anyFailed;
      Batch updatedBatch =
          executeInTransaction(
              () -> {
                Batch fresh = batchRepository.findByBatchId(batchId).orElse(batch);
                if (failed) {
                  fresh.setStatus(BatchStatus.PARTIAL_FAILURE);
                } else {
                  fresh.setStatus(BatchStatus.PAYOUTS_REQUESTED);
                }
                return batchRepository.save(fresh);
              });

      return BatchResponse.fromEntity(updatedBatch);
    } finally {
      releaseSessionAdvisoryLock(lock);
    }
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
