package io.github.yesh.paycore.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.yesh.paycore.settlement.domain.Batch;
import io.github.yesh.paycore.settlement.domain.BatchStatus;
import io.github.yesh.paycore.settlement.domain.SettlementRecord;
import io.github.yesh.paycore.settlement.domain.SettlementRecordStatus;
import io.github.yesh.paycore.settlement.dto.BatchCutoffRequest;
import io.github.yesh.paycore.settlement.dto.BatchResponse;
import io.github.yesh.paycore.settlement.repository.BatchRepository;
import io.github.yesh.paycore.settlement.repository.SettlementPayoutRepository;
import io.github.yesh.paycore.settlement.repository.SettlementRecordRepository;
import io.github.yesh.paycore.settlement.service.BatchCutoffService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BatchCutoffServiceTest {

  private BatchRepository batchRepo;
  private SettlementRecordRepository recordRepo;
  private SettlementPayoutRepository payoutRepo;
  private BatchCutoffService cutoffService;

  @BeforeEach
  void setUp() {
    batchRepo = mock(BatchRepository.class);
    recordRepo = mock(SettlementRecordRepository.class);
    payoutRepo = mock(SettlementPayoutRepository.class);
    cutoffService = new BatchCutoffService(batchRepo, recordRepo, payoutRepo);
  }

  @Test
  @DisplayName("Should run batch cutoff and aggregate totals accurately")
  void testRunCutoffWithPendingRecords() {
    SettlementRecord r1 =
        new SettlementRecord(
            "REC_1",
            "PAY_1",
            "SELLER_A",
            "ptxn_1",
            "ord_1",
            "CAPTURE",
            100000L,
            2000L,
            98000L,
            Instant.now());
    SettlementRecord r2 =
        new SettlementRecord(
            "REC_2",
            "PAY_2",
            "SELLER_B",
            "ptxn_2",
            "ord_2",
            "CAPTURE",
            200000L,
            4000L,
            196000L,
            Instant.now());

    when(recordRepo.findByBatchIdIsNullAndOccurredAtLessThanEqual(any(Instant.class)))
        .thenReturn(List.of(r1, r2));

    when(batchRepo.save(any(Batch.class))).thenAnswer(inv -> inv.getArgument(0));

    BatchResponse response =
        cutoffService.runCutoff(new BatchCutoffRequest(Instant.now(), 10000L, null));

    assertThat(response).isNotNull();
    assertThat(response.totalCount()).isEqualTo(2);
    assertThat(response.totalGrossPaise()).isEqualTo(300000L);
    assertThat(response.totalFeePaise()).isEqualTo(6000L);
    assertThat(response.totalNetPaise()).isEqualTo(294000L);
    assertThat(response.status()).isEqualTo(BatchStatus.COMPLETED);

    assertThat(r1.getStatus()).isEqualTo(SettlementRecordStatus.BATCHED);
    assertThat(r2.getStatus()).isEqualTo(SettlementRecordStatus.BATCHED);

    verify(batchRepo).save(any(Batch.class));
    verify(recordRepo).saveAll(any());
    verify(payoutRepo).saveAll(any());
  }

  @Test
  @DisplayName("Should safely handle empty batch cutoff (Re-run safety)")
  void testRunCutoffEmpty() {
    when(recordRepo.findByBatchIdIsNullAndOccurredAtLessThanEqual(any(Instant.class)))
        .thenReturn(List.of());
    when(batchRepo.save(any(Batch.class))).thenAnswer(inv -> inv.getArgument(0));

    BatchResponse response =
        cutoffService.runCutoff(new BatchCutoffRequest(Instant.now(), 10000L, null));

    assertThat(response).isNotNull();
    assertThat(response.totalCount()).isEqualTo(0);
    assertThat(response.totalGrossPaise()).isEqualTo(0L);
    assertThat(response.status()).isEqualTo(BatchStatus.CLOSED);

    verify(payoutRepo, never()).saveAll(any());
  }
}
