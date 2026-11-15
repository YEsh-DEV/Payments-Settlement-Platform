package io.github.yesh.paycore.settlement.service;

import io.github.yesh.paycore.settlement.domain.SettlementRecord;
import io.github.yesh.paycore.settlement.domain.SimulationManifest;
import io.github.yesh.paycore.settlement.dto.SimulationRequest;
import io.github.yesh.paycore.settlement.dto.SimulationResult;
import io.github.yesh.paycore.settlement.repository.SettlementRecordRepository;
import io.github.yesh.paycore.settlement.repository.SimulationManifestRepository;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderSimulatorService {

  private static final Logger log = LoggerFactory.getLogger(ProviderSimulatorService.class);

  private final SettlementRecordRepository recordRepository;
  private final SimulationManifestRepository manifestRepository;

  public ProviderSimulatorService(
      SettlementRecordRepository recordRepository,
      SimulationManifestRepository manifestRepository) {
    this.recordRepository = recordRepository;
    this.manifestRepository = manifestRepository;
  }

  @Transactional
  public SimulationResult generateSettlementFile(SimulationRequest request) {
    long seed = (request != null && request.seed() != null) ? request.seed() : 42L;
    Random random = new Random(seed);

    String batchId =
        (request != null && request.batchId() != null) ? request.batchId() : "BATCH_SIM";
    List<SettlementRecord> records = recordRepository.findByBatchId(batchId);

    if (records.isEmpty()) {
      records = recordRepository.findByBatchIdIsNull();
    }

    String filename =
        (request != null && request.filename() != null)
            ? request.filename()
            : "settlement_" + batchId + "_" + System.currentTimeMillis() + ".csv";

    List<SimulationManifest> manifests = new ArrayList<>();
    StringWriter out = new StringWriter();

    CSVFormat csvFormat =
        CSVFormat.DEFAULT
            .builder()
            .setHeader(
                "provider_batch_id",
                "provider_txn_id",
                "txn_type",
                "merchant_ref",
                "gross_amount",
                "fee_amount",
                "net_amount",
                "currency",
                "txn_time",
                "settle_date")
            .build();

    try (CSVPrinter printer = new CSVPrinter(out, csvFormat)) {
      int lineCount = 0;
      long totalGrossPaise = 0L;
      LocalDate today = LocalDate.now();

      for (SettlementRecord record : records) {
        double roll = random.nextDouble() * 100.0;

        // Fault 1: Missing provider line (0.5%)
        if (roll < 0.5) {
          manifests.add(
              new SimulationManifest(
                  batchId,
                  filename,
                  "FAULT_MISSING_PROVIDER_LINE",
                  record.getRecordId(),
                  "Candidate dropped from provider settlement file"));
          continue;
        }

        String providerTxnId =
            record.getProviderTxnId() != null
                ? record.getProviderTxnId()
                : "ptxn_" + UUID.randomUUID().toString().substring(0, 12);
        long gross = record.getGrossPaise();
        long fee = record.getFeePaise();
        LocalDate settleDate = today;

        // Fault 2: Gross amount differs (0.5%)
        if (roll >= 0.5 && roll < 1.0) {
          long delta = 100L + random.nextInt(400);
          gross += delta;
          manifests.add(
              new SimulationManifest(
                  batchId,
                  filename,
                  "FAULT_GROSS_AMOUNT_MISMATCH",
                  record.getRecordId(),
                  "Gross amount modified from " + record.getGrossPaise() + " to " + gross));
        }

        // Fault 3: Fee differs from fee model (1.0%)
        if (roll >= 1.0 && roll < 2.0) {
          fee += 200L;
          manifests.add(
              new SimulationManifest(
                  batchId,
                  filename,
                  "FAULT_FEE_VARIANCE",
                  record.getRecordId(),
                  "Fee modified from " + record.getFeePaise() + " to " + fee));
        }

        // Fault 4: Delayed to later day (1.0%)
        if (roll >= 2.0 && roll < 3.0) {
          settleDate = today.plusDays(4);
          manifests.add(
              new SimulationManifest(
                  batchId,
                  filename,
                  "FAULT_LINE_DELAYED",
                  record.getRecordId(),
                  "Settle date delayed to " + settleDate));
        }

        long net = gross - fee;
        String merchantRef =
            record.getOrderId() != null ? record.getOrderId() : record.getPaymentId();

        printer.printRecord(
            batchId,
            providerTxnId,
            record.getKind() != null ? record.getKind() : "CAPTURE",
            merchantRef,
            gross,
            fee,
            net,
            "INR",
            DateTimeFormatter.ISO_INSTANT.format(record.getOccurredAt()),
            settleDate.toString());

        lineCount++;
        totalGrossPaise += gross;

        // Fault 5: Duplicate provider line (0.3%)
        if (roll >= 3.0 && roll < 3.3) {
          printer.printRecord(
              batchId,
              providerTxnId,
              record.getKind() != null ? record.getKind() : "CAPTURE",
              merchantRef,
              gross,
              fee,
              net,
              "INR",
              DateTimeFormatter.ISO_INSTANT.format(record.getOccurredAt()),
              settleDate.toString());

          lineCount++;
          totalGrossPaise += gross;

          manifests.add(
              new SimulationManifest(
                  batchId,
                  filename,
                  "FAULT_DUPLICATE_PROVIDER_LINE",
                  providerTxnId,
                  "Duplicate line emitted for " + providerTxnId));
        }
      }

      // Fault 6: Ghost provider line with no ledger item (0.2%)
      if (random.nextDouble() * 100.0 < 50.0 && !records.isEmpty()) {
        String ghostTxnId = "ghost_ptxn_" + UUID.randomUUID().toString().substring(0, 10);
        long ghostGross = 49900L;
        long ghostFee = 998L;
        long ghostNet = ghostGross - ghostFee;

        printer.printRecord(
            batchId,
            ghostTxnId,
            "CAPTURE",
            "ghost_order_" + UUID.randomUUID().toString().substring(0, 8),
            ghostGross,
            ghostFee,
            ghostNet,
            "INR",
            DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            today.toString());

        lineCount++;
        totalGrossPaise += ghostGross;

        manifests.add(
            new SimulationManifest(
                batchId,
                filename,
                "FAULT_MISSING_LEDGER_ITEM",
                ghostTxnId,
                "Provider line with no matching ledger transaction"));
      }

      // Output Trailer Row
      printer.printRecord("TRAILER", lineCount, totalGrossPaise);
      printer.flush();

      if (!manifests.isEmpty()) {
        manifestRepository.saveAll(manifests);
      }

      log.info(
          "Simulated settlement file {} generated: {} lines, {} paise, {} faults injected",
          filename,
          lineCount,
          totalGrossPaise,
          manifests.size());

      return new SimulationResult(
          batchId, filename, out.toString(), manifests.size(), lineCount, totalGrossPaise);

    } catch (Exception e) {
      log.error("Failed to generate simulated settlement file", e);
      throw new RuntimeException("Simulation error: " + e.getMessage(), e);
    }
  }

  @Transactional(readOnly = true)
  public List<SimulationManifest> getManifests(String batchId) {
    return manifestRepository.findByBatchId(batchId);
  }
}
