package io.github.yesh.paycore.settlement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.settlement.domain.BatchStatus;
import io.github.yesh.paycore.settlement.dto.BatchCutoffRequest;
import io.github.yesh.paycore.settlement.dto.BatchResponse;
import io.github.yesh.paycore.settlement.service.BatchCutoffService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class BatchControllerTest {

  private BatchCutoffService service;
  private BatchController controller;

  @BeforeEach
  void setUp() {
    service = mock(BatchCutoffService.class);
    controller = new BatchController(service);
  }

  @Test
  @DisplayName("F-S-01: Trigger cutoff batch returns 200 and batch summary")
  void testRunCutoff() {
    BatchResponse expected =
        new BatchResponse(
            "BATCH_1",
            "2026-11-16T12:00:00Z",
            BatchStatus.CREATED,
            5,
            50000L,
            1000L,
            49000L,
            Instant.now(),
            null);
    when(service.runCutoff(any())).thenReturn(expected);

    ResponseEntity<BatchResponse> response =
        controller.runCutoff(new BatchCutoffRequest(Instant.now(), null, null));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expected);
  }

  @Test
  @DisplayName("F-S-05: Query batch by ID returns batch response")
  void testGetBatch() {
    BatchResponse expected =
        new BatchResponse(
            "BATCH_1",
            "2026-11-16T12:00:00Z",
            BatchStatus.SETTLED,
            5,
            50000L,
            1000L,
            49000L,
            Instant.now(),
            Instant.now());
    when(service.getBatch("BATCH_1")).thenReturn(expected);

    ResponseEntity<BatchResponse> response = controller.getBatch("BATCH_1");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expected);
  }

  @Test
  @DisplayName("F-S-05: Query non-existent batch throws 404")
  void testGetNonExistentBatchThrows() {
    when(service.getBatch("UNKNOWN"))
        .thenThrow(
            new PayCoreException(
                HttpStatus.NOT_FOUND, "batch_not_found", "Batch UNKNOWN not found"));

    assertThatThrownBy(() -> controller.getBatch("UNKNOWN"))
        .isInstanceOf(PayCoreException.class)
        .hasMessageContaining("not found");
  }

  @Test
  @DisplayName("F-S-05: List batches returns paginated results")
  void testListBatches() {
    BatchResponse b1 =
        new BatchResponse(
            "BATCH_1",
            "2026-11-16T12:00:00Z",
            BatchStatus.CREATED,
            2,
            20000L,
            400L,
            19600L,
            Instant.now(),
            null);
    Page<BatchResponse> page = new PageImpl<>(List.of(b1));
    when(service.listBatches(any())).thenReturn(page);

    ResponseEntity<Page<BatchResponse>> response = controller.listBatches(PageRequest.of(0, 10));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getContent()).containsExactly(b1);
  }
}
