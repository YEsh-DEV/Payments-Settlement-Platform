package io.github.yesh.paycore.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.yesh.paycore.settlement.domain.ProviderSettlementFile;
import io.github.yesh.paycore.settlement.dto.FileUploadResponse;
import io.github.yesh.paycore.settlement.exception.FileAlreadyIngestedException;
import io.github.yesh.paycore.settlement.exception.FileIntegrityException;
import io.github.yesh.paycore.settlement.repository.ProviderLineRepository;
import io.github.yesh.paycore.settlement.repository.ProviderSettlementFileRepository;
import io.github.yesh.paycore.settlement.service.StreamingFileParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StreamingFileParserTest {

  private ProviderSettlementFileRepository fileRepo;
  private ProviderLineRepository lineRepo;
  private StreamingFileParser parser;

  @BeforeEach
  void setUp() {
    fileRepo = mock(ProviderSettlementFileRepository.class);
    lineRepo = mock(ProviderLineRepository.class);
    parser = new StreamingFileParser(fileRepo, lineRepo);
  }

  @Test
  @DisplayName("Should successfully parse streaming CSV with trailer checksum")
  void testParseValidCsvWithTrailer() {
    String csv =
        """
        provider_batch_id,provider_txn_id,txn_type,merchant_ref,gross_amount,fee_amount,net_amount,currency,txn_time,settle_date
        B_01,ptxn_1,CAPTURE,ord_1,100000,2000,98000,INR,2026-11-15T10:00:00Z,2026-11-15
        B_01,ptxn_2,CAPTURE,ord_2,200000,4000,196000,INR,2026-11-15T11:00:00Z,2026-11-15
        TRAILER,2,300000
        """;

    when(fileRepo.existsByFileHash(anyString())).thenReturn(false);
    when(fileRepo.save(any(ProviderSettlementFile.class))).thenAnswer(inv -> inv.getArgument(0));

    FileUploadResponse response =
        parser.parseAndIngest(
            "test_settlement.csv",
            "RAZORPAY_SIM",
            new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    assertThat(response).isNotNull();
    assertThat(response.totalRecords()).isEqualTo(2);
    assertThat(response.totalAmountPaise()).isEqualTo(300000L);
    assertThat(response.filename()).isEqualTo("test_settlement.csv");

    verify(lineRepo).saveAll(any());
  }

  @Test
  @DisplayName("Should reject file when trailer checksum mismatches")
  void testTrailerChecksumMismatch() {
    String csv =
        """
        provider_batch_id,provider_txn_id,txn_type,merchant_ref,gross_amount,fee_amount,net_amount,currency,txn_time,settle_date
        B_01,ptxn_1,CAPTURE,ord_1,100000,2000,98000,INR,2026-11-15T10:00:00Z,2026-11-15
        TRAILER,1,999999
        """;

    when(fileRepo.existsByFileHash(anyString())).thenReturn(false);
    when(fileRepo.save(any(ProviderSettlementFile.class))).thenAnswer(inv -> inv.getArgument(0));

    assertThatThrownBy(
            () ->
                parser.parseAndIngest(
                    "corrupt.csv",
                    "RAZORPAY_SIM",
                    new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(FileIntegrityException.class)
        .hasMessageContaining("File integrity error");
  }

  @Test
  @DisplayName("Should reject re-uploading identical file (FileAlreadyIngestedException)")
  void testRejectDuplicateFileHash() {
    String csv =
        """
        provider_batch_id,provider_txn_id,txn_type,merchant_ref,gross_amount,fee_amount,net_amount,currency,txn_time,settle_date
        B_01,ptxn_1,CAPTURE,ord_1,100000,2000,98000,INR,2026-11-15T10:00:00Z,2026-11-15
        TRAILER,1,100000
        """;

    when(fileRepo.save(any(ProviderSettlementFile.class))).thenAnswer(inv -> inv.getArgument(0));
    when(fileRepo.existsByFileHash(anyString())).thenReturn(true);

    assertThatThrownBy(
            () ->
                parser.parseAndIngest(
                    "duplicate.csv",
                    "RAZORPAY_SIM",
                    new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(FileAlreadyIngestedException.class);
  }
}
