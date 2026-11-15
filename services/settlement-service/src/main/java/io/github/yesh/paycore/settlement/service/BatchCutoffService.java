package io.github.yesh.paycore.settlement.service;

import io.github.yesh.paycore.settlement.domain.Batch;
import io.github.yesh.paycore.settlement.domain.BatchStatus;
import io.github.yesh.paycore.settlement.domain.SettlementPayout;
import io.github.yesh.paycore.settlement.domain.SettlementRecord;
import io.github.yesh.paycore.settlement.domain.SettlementRecordStatus;
import io.github.yesh.paycore.settlement.dto.BatchCutoffRequest;
import io.github.yesh.paycore.settlement.dto.BatchResponse;
import io.github.yesh.paycore.settlement.exception.BatchNotFoundException;
import io.github.yesh.paycore.settlement.repository.BatchRepository;
import io.github.yesh.paycore.settlement.repository.SettlementPayoutRepository;
import io.github.yesh.paycore.settlement.repository.SettlementRecordRepository;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchCutoffService {

  private static final Logger log = LoggerFactory.getLogger(BatchCutoffService.class);

  private final BatchRepository batchRepository;
  private final SettlementRecordRepository settlementRecordRepository;
  private final SettlementPayoutRepository settlementPayoutRepository;

  public BatchCutoffService(
      BatchRepository batchRepository,
      SettlementRecordRepository settlementRecordRepository,
      SettlementPayoutRepository settlementPayoutRepository) {
    this.batchRepository = batchRepository;
    this.settlementRecordRepository = settlementRecordRepository;
    this.settlementPayoutRepository = settlementPayoutRepository;
  }

  @Transactional
  public BatchResponse runCutoff(BatchCutoffRequest request) {
    Instant cutoffTime =
        (request != null && request.cutoffTime() != null) ? request.cutoffTime() : Instant.now();

    long minPayoutAmount =
        (request != null && request.minPayoutAmountPaise() != null)
            ? request.minPayoutAmountPaise()
            : 10000L; // Rs 100 default

    List<SettlementRecord> pending =
        settlementRecordRepository.findByBatchIdIsNullAndOccurredAtLessThanEqual(cutoffTime);

    String batchId =
        "BATCH_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
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
    batch.setStatus(BatchStatus.COMPLETED);
    batch.setClosedAt(Instant.now());

    batchRepository.save(batch);
    settlementRecordRepository.saveAll(pending);

    // Create payouts per seller with deterministic IDs
    List<SettlementPayout> payouts = new ArrayList<>();
    for (Map.Entry<String, Long> entry : sellerTotals.entrySet()) {
      String sellerId = entry.getKey();
      long netAmount = entry.getValue();

      if (netAmount >= minPayoutAmount) {
        String payoutId = batchId + ":" + sellerId;
        SettlementPayout payout = new SettlementPayout(payoutId, batchId, sellerId, netAmount);
        payouts.add(payout);
      }
    }

    if (!payouts.isEmpty()) {
      settlementPayoutRepository.saveAll(payouts);
      log.info("Generated {} payouts for batch {}", payouts.size(), batchId);
    }

    log.info("Cutoff batch {} closed with {} records, net: {} paise", batchId, count, totalNet);

    return BatchResponse.fromEntity(batch);
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
