package io.github.yesh.paycore.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.yesh.paycore.settlement.client.LedgerClient;
import io.github.yesh.paycore.settlement.domain.Batch;
import io.github.yesh.paycore.settlement.domain.BatchStatus;
import io.github.yesh.paycore.settlement.domain.SettlementPayout;
import io.github.yesh.paycore.settlement.domain.SettlementRecord;
import io.github.yesh.paycore.settlement.domain.SettlementRecordStatus;
import io.github.yesh.paycore.settlement.dto.BatchCutoffRequest;
import io.github.yesh.paycore.settlement.dto.BatchResponse;
import io.github.yesh.paycore.settlement.exception.InvalidTransitionException;
import io.github.yesh.paycore.settlement.repository.BatchRepository;
import io.github.yesh.paycore.settlement.repository.SettlementPayoutRepository;
import io.github.yesh.paycore.settlement.repository.SettlementRecordRepository;
import io.github.yesh.paycore.settlement.service.BatchCutoffService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BatchCutoffServiceTest {

  private BatchRepository batchRepo;
  private SettlementRecordRepository recordRepo;
  private SettlementPayoutRepository payoutRepo;
  private LedgerClient ledgerClient;
  private BatchCutoffService cutoffService;

  @BeforeEach
  void setUp() {
    batchRepo = mock(BatchRepository.class);
    recordRepo = mock(SettlementRecordRepository.class);
    payoutRepo = mock(SettlementPayoutRepository.class);
    ledgerClient = mock(LedgerClient.class);
    cutoffService = new BatchCutoffService(batchRepo, recordRepo, payoutRepo, ledgerClient, null);
  }

  @Test
  @DisplayName("Should run batch cutoff, initiate ledger payouts, and transition to PAYOUTS_REQUESTED")
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
    assertThat(response.status()).isEqualTo(BatchStatus.PAYOUTS_REQUESTED);

    assertThat(r1.getStatus()).isEqualTo(SettlementRecordStatus.BATCHED);
    assertThat(r2.getStatus()).isEqualTo(SettlementRecordStatus.BATCHED);

    verify(batchRepo, atLeastOnce()).save(any(Batch.class));
    verify(recordRepo).saveAll(any());
    verify(payoutRepo).saveAll(any());
    verify(ledgerClient, times(2)).initiatePayout(any(), any(), anyLong());
  }

  @Test
  @DisplayName("Full Payout Lifecycle: PAYOUTS_REQUESTED -> sendPayoutFile -> SENT -> processBankResults -> COMPLETED")
  void testFullPayoutLifecycle() {
    String batchId = "BATCH_TEST_101";
    Batch batch = new Batch(batchId, "2026-11-16T00:00:00Z");
    batch.setStatus(BatchStatus.PAYOUTS_REQUESTED);

    SettlementPayout p1 = new SettlementPayout(batchId + ":SELLER_A", batchId, "SELLER_A", 98000L);
    p1.setStatus("REQUESTED");

    when(batchRepo.findByBatchId(batchId)).thenReturn(Optional.of(batch));
    when(batchRepo.save(any(Batch.class))).thenAnswer(inv -> inv.getArgument(0));
    when(payoutRepo.findByBatchId(batchId)).thenReturn(List.of(p1));
    when(payoutRepo.save(any(SettlementPayout.class))).thenAnswer(inv -> inv.getArgument(0));

    // 1. Send Payout File
    String csv = cutoffService.sendPayoutFile(batchId);
    assertThat(csv).contains("payout_id,seller_id,amount,reference");
    assertThat(csv).contains(p1.getPayoutId());
    assertThat(batch.getStatus()).isEqualTo(BatchStatus.SENT);
    assertThat(p1.getStatus()).isEqualTo("SENT");

    // 2. Process Bank Confirmation Results
    String bankResultCsv = "payout_id,status,reason\n" + p1.getPayoutId() + ",CONFIRMED,SUCCESS\n";
    BatchResponse finalBatch = cutoffService.processBankResults(batchId, bankResultCsv);

    assertThat(finalBatch.status()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(p1.getStatus()).isEqualTo("CONFIRMED");
    verify(ledgerClient).confirmPayout(p1.getPayoutId(), batchId);
  }

  @Test
  @DisplayName("Illegal transition returns 409: Cannot confirm a FAILED payout")
  void testIllegalTransitionThrows409() {
    String batchId = "BATCH_ERR";
    Batch batch = new Batch(batchId, "2026-11-16T00:00:00Z");
    batch.setStatus(BatchStatus.SENT);

    SettlementPayout p1 = new SettlementPayout(batchId + ":SELLER_A", batchId, "SELLER_A", 98000L);
    p1.setStatus("FAILED"); // Already terminal FAILED

    when(batchRepo.findByBatchId(batchId)).thenReturn(Optional.of(batch));
    when(payoutRepo.findByBatchId(batchId)).thenReturn(List.of(p1));

    String bankResultCsv = "payout_id,status,reason\n" + p1.getPayoutId() + ",CONFIRMED,SUCCESS\n";
    assertThatThrownBy(() -> cutoffService.processBankResults(batchId, bankResultCsv))
        .isInstanceOf(InvalidTransitionException.class)
        .hasMessageContaining("Cannot confirm payout");
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
