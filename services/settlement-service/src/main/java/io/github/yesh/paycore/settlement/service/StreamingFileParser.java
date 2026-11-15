package io.github.yesh.paycore.settlement.service;

import io.github.yesh.paycore.settlement.domain.ProviderFileStatus;
import io.github.yesh.paycore.settlement.domain.ProviderLine;
import io.github.yesh.paycore.settlement.domain.ProviderSettlementFile;
import io.github.yesh.paycore.settlement.dto.FileUploadResponse;
import io.github.yesh.paycore.settlement.exception.FileAlreadyIngestedException;
import io.github.yesh.paycore.settlement.exception.FileIntegrityException;
import io.github.yesh.paycore.settlement.repository.ProviderLineRepository;
import io.github.yesh.paycore.settlement.repository.ProviderSettlementFileRepository;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StreamingFileParser {

  private static final Logger log = LoggerFactory.getLogger(StreamingFileParser.class);
  private static final int BATCH_SIZE = 1000;

  private final ProviderSettlementFileRepository fileRepository;
  private final ProviderLineRepository lineRepository;

  public StreamingFileParser(
      ProviderSettlementFileRepository fileRepository, ProviderLineRepository lineRepository) {
    this.fileRepository = fileRepository;
    this.lineRepository = lineRepository;
  }

  @Transactional
  public FileUploadResponse parseAndIngest(
      String filename, String provider, InputStream inputStream) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      DigestInputStream dis = new DigestInputStream(inputStream, md);
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(dis, StandardCharsets.UTF_8));

      CSVFormat csvFormat =
          CSVFormat.DEFAULT
              .builder()
              .setHeader()
              .setSkipHeaderRecord(true)
              .setIgnoreSurroundingSpaces(true)
              .build();

      CSVParser parser = csvFormat.parse(reader);

      List<ProviderLine> lineBuffer = new ArrayList<>(BATCH_SIZE);
      int parsedCount = 0;
      long totalGrossPaise = 0L;
      int lineNumber = 1;

      Integer trailerCount = null;
      Long trailerAmount = null;

      // Temporary file record with placeholder hash
      ProviderSettlementFile fileRecord =
          new ProviderSettlementFile(filename, "PENDING_HASH", provider, 0, 0L);
      fileRecord = fileRepository.save(fileRecord);

      for (CSVRecord record : parser) {
        lineNumber++;
        String firstCol = record.get(0);

        // Detect Trailer row
        if (firstCol != null
            && (firstCol.equalsIgnoreCase("TRAILER")
                || firstCol.equalsIgnoreCase("TOTAL_COUNT")
                || firstCol.toUpperCase().startsWith("TRAILER"))) {
          if (record.size() >= 3) {
            trailerCount = Integer.parseInt(record.get(1).trim());
            trailerAmount = Long.parseLong(record.get(2).trim());
          } else if (record.size() >= 2) {
            trailerCount = Integer.parseInt(record.get(1).trim());
            trailerAmount = totalGrossPaise; // Fallback if only count in trailer
          }
          break;
        }

        String providerBatchId = record.get("provider_batch_id");
        String providerTxnId = record.get("provider_txn_id");
        String txnType = record.isMapped("txn_type") ? record.get("txn_type") : "CAPTURE";
        String merchantRef = record.isMapped("merchant_ref") ? record.get("merchant_ref") : null;
        String internalRef =
            record.isMapped("internal_reference") ? record.get("internal_reference") : merchantRef;

        long grossAmount = Long.parseLong(record.get("gross_amount").trim());
        long feeAmount =
            record.isMapped("fee_amount") ? Long.parseLong(record.get("fee_amount").trim()) : 0L;
        long netAmount =
            record.isMapped("net_amount")
                ? Long.parseLong(record.get("net_amount").trim())
                : (grossAmount - feeAmount);

        String currency = record.isMapped("currency") ? record.get("currency") : "INR";
        Instant txnTime =
            record.isMapped("txn_time")
                ? Instant.parse(record.get("txn_time").trim())
                : Instant.now();
        LocalDate settleDate =
            record.isMapped("settle_date")
                ? LocalDate.parse(record.get("settle_date").trim())
                : LocalDate.now();

        ProviderLine line =
            new ProviderLine(
                fileRecord.getId(),
                lineNumber,
                providerBatchId,
                providerTxnId,
                txnType,
                merchantRef,
                internalRef,
                grossAmount,
                feeAmount,
                netAmount,
                currency,
                txnTime,
                settleDate,
                record.toString());

        lineBuffer.add(line);
        parsedCount++;
        totalGrossPaise += grossAmount;

        if (lineBuffer.size() >= BATCH_SIZE) {
          lineRepository.saveAll(lineBuffer);
          lineBuffer.clear();
        }
      }

      if (!lineBuffer.isEmpty()) {
        lineRepository.saveAll(lineBuffer);
        lineBuffer.clear();
      }

      // Compute final SHA-256
      String fileHash = HexFormat.of().formatHex(md.digest());

      // Check SHA-256 duplicate constraint
      if (fileRepository.existsByFileHash(fileHash)) {
        throw new FileAlreadyIngestedException(
            "File with SHA-256 " + fileHash + " already ingested");
      }

      // Validate trailer checksum if present
      if (trailerCount != null && trailerCount != parsedCount) {
        throw new FileIntegrityException(
            "File integrity error: trailer count "
                + trailerCount
                + " does not match parsed count "
                + parsedCount);
      }
      if (trailerAmount != null && trailerAmount != totalGrossPaise) {
        throw new FileIntegrityException(
            "File integrity error: trailer amount "
                + trailerAmount
                + " does not match parsed amount "
                + totalGrossPaise);
      }

      fileRecord =
          new ProviderSettlementFile(filename, fileHash, provider, parsedCount, totalGrossPaise);
      fileRecord.setStatus(ProviderFileStatus.INGESTED);
      ProviderSettlementFile saved = fileRepository.save(fileRecord);

      log.info(
          "Successfully parsed and ingested file {} ({} records, {} paise, hash {})",
          filename,
          parsedCount,
          totalGrossPaise,
          fileHash);

      return FileUploadResponse.fromEntity(saved);

    } catch (FileAlreadyIngestedException | FileIntegrityException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to parse provider settlement file: {}", filename, e);
      throw new FileIntegrityException("Failed to parse file: " + e.getMessage());
    }
  }
}
