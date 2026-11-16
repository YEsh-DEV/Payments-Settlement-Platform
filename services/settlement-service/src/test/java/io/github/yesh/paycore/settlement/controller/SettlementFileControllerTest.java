package io.github.yesh.paycore.settlement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.yesh.paycore.settlement.domain.ProviderFileStatus;
import io.github.yesh.paycore.settlement.dto.FileUploadResponse;
import io.github.yesh.paycore.settlement.service.StreamingFileParser;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

public class SettlementFileControllerTest {

  private StreamingFileParser streamingFileParser;
  private SettlementFileController controller;

  @BeforeEach
  void setUp() {
    streamingFileParser = mock(StreamingFileParser.class);
    controller = new SettlementFileController(streamingFileParser);
  }

  @Test
  @DisplayName("F-S-03: Upload settlement CSV file parses and ingests file")
  void testUploadFile() throws IOException {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "settlement_20261116.csv", "text/csv", "line1\nline2".getBytes());

    FileUploadResponse expected =
        new FileUploadResponse(
            UUID.randomUUID(),
            "settlement_20261116.csv",
            "hash123",
            "RAZORPAY_SIM",
            2,
            20000L,
            ProviderFileStatus.INGESTED,
            Instant.now());

    when(streamingFileParser.parseAndIngest(
            eq("settlement_20261116.csv"), eq("RAZORPAY_SIM"), any(InputStream.class)))
        .thenReturn(expected);

    ResponseEntity<FileUploadResponse> response = controller.uploadFile(file, "RAZORPAY_SIM");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expected);
  }

  @Test
  @DisplayName("F-S-03: List ingested settlement files returns paginated response")
  void testListFiles() {
    FileUploadResponse f1 =
        new FileUploadResponse(
            UUID.randomUUID(),
            "file_1.csv",
            "hash1",
            "RAZORPAY_SIM",
            10,
            100000L,
            ProviderFileStatus.INGESTED,
            Instant.now());
    Page<FileUploadResponse> page = new PageImpl<>(List.of(f1));
    when(streamingFileParser.listFiles(any())).thenReturn(page);

    ResponseEntity<Page<FileUploadResponse>> response = controller.listFiles(PageRequest.of(0, 10));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getContent()).containsExactly(f1);
  }
}
