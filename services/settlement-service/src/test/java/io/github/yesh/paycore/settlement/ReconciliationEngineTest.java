package io.github.yesh.paycore.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.settlement.domain.*;
import io.github.yesh.paycore.settlement.dto.ReconciliationRunRequest;
import io.github.yesh.paycore.settlement.dto.ReconciliationRunResponse;
import io.github.yesh.paycore.settlement.reconciliation.ReconciliationEngine;
import io.github.yesh.paycore.settlement.repository.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReconciliationEngineTest {

  private ReconciliationRunRepository runRepo;
  private ReconciliationMatchRepository matchRepo;
  private SettlementRecordRepository recordRepo;
  private ProviderLineRepository lineRepo;
  private BatchRepository batchRepo;
  private BankDepositRepository depositRepo;
  private CaseRepository caseRepo;
  private SimulationManifestRepository manifestRepo;
  private ReconciliationEngine engine;

  @BeforeEach
  void setUp() {
    runRepo = mock(ReconciliationRunRepository.class);
    matchRepo = mock(ReconciliationMatchRepository.class);
    recordRepo = mock(SettlementRecordRepository.class);
    lineRepo = mock(ProviderLineRepository.class);
    batchRepo = mock(BatchRepository.class);
    depositRepo = mock(BankDepositRepository.class);
    caseRepo = mock(CaseRepository.class);
    manifestRepo = mock(SimulationManifestRepository.class);

    when(runRepo.save(any(ReconciliationRun.class))).thenAnswer(inv -> inv.getArgument(0));

    engine =
        new ReconciliationEngine(
            runRepo,
            matchRepo,
            recordRepo,
            lineRepo,
            batchRepo,
            depositRepo,
            caseRepo,
            manifestRepo,
            new ObjectMapper());
  }

  @Test
  @DisplayName("Stage 1: Exact line matching and fee variance detection")
  void testStage1ExactMatchAndFeeVariance() {
    String batchId = "BATCH_01";
    SettlementRecord r1 =
        new SettlementRecord(
            "REC_1",
            "PAY_1",
            "SEL_1",
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
            "SEL_2",
            "ptxn_2",
            "ord_2",
            "CAPTURE",
            200000L,
            4000L,
            196000L,
            Instant.now());

    ProviderLine l1 =
        new ProviderLine(
            UUID.randomUUID(),
            1,
            batchId,
            "ptxn_1",
            "CAPTURE",
            "ord_1",
            "ord_1",
            100000L,
            2000L,
            98000L,
            "INR",
            Instant.now(),
            LocalDate.now(),
            null);
    l1.setId(101L);
    // l2 has fee variance (4200 instead of 4000)
    ProviderLine l2 =
        new ProviderLine(
            UUID.randomUUID(),
            2,
            batchId,
            "ptxn_2",
            "CAPTURE",
            "ord_2",
            "ord_2",
            200000L,
            4200L,
            195800L,
            "INR",
            Instant.now(),
            LocalDate.now(),
            null);
    l2.setId(102L);

    when(recordRepo.findByBatchId(batchId)).thenReturn(List.of(r1, r2));
    when(lineRepo.findByProviderBatchId(batchId)).thenReturn(List.of(l1, l2));

    ReconciliationRunResponse response =
        engine.execute(new ReconciliationRunRequest(batchId, null, null, 200, 1L));

    assertThat(response).isNotNull();
    assertThat(response.matchedCount()).isEqualTo(1); // r1 exact matched
    assertThat(response.discrepancyCount()).isEqualTo(1); // r2 fee variance
    assertThat(response.totalVariancePaise()).isEqualTo(200L); // 4200 - 4000 = 200
    assertThat(response.status()).isEqualTo(ReconciliationStatus.COMPLETED);

    verify(matchRepo).saveAll(anyList());
    verify(caseRepo).saveAll(anyList());
  }

  @Test
  @DisplayName("Stage 1: Duplicate provider line detection and amount mismatch")
  void testStage1DuplicateLineAndAmountMismatch() {
    String batchId = "BATCH_02";
    SettlementRecord r1 =
        new SettlementRecord(
            "REC_1",
            "PAY_1",
            "SEL_1",
            "ptxn_1",
            "ord_1",
            "CAPTURE",
            100000L,
            2000L,
            98000L,
            Instant.now());

    // Two provider lines with same ptxn_1
    ProviderLine l1 =
        new ProviderLine(
            UUID.randomUUID(),
            1,
            batchId,
            "ptxn_1",
            "CAPTURE",
            "ord_1",
            "ord_1",
            100000L,
            2000L,
            98000L,
            "INR",
            Instant.now(),
            LocalDate.now(),
            null);
    l1.setId(201L);
    ProviderLine l1Dup =
        new ProviderLine(
            UUID.randomUUID(),
            2,
            batchId,
            "ptxn_1",
            "CAPTURE",
            "ord_1",
            "ord_1",
            100000L,
            2000L,
            98000L,
            "INR",
            Instant.now(),
            LocalDate.now(),
            null);
    l1Dup.setId(202L);

    when(recordRepo.findByBatchId(batchId)).thenReturn(List.of(r1));
    when(lineRepo.findByProviderBatchId(batchId)).thenReturn(List.of(l1, l1Dup));

    ReconciliationRunResponse response =
        engine.execute(new ReconciliationRunRequest(batchId, null, null, 200, 1L));

    assertThat(response).isNotNull();
    assertThat(response.discrepancyCount()).isGreaterThanOrEqualTo(1); // duplicate line caught
  }

  @Test
  @DisplayName("Stage 2: Fuzzy fallback via merchantRef orderId")
  void testStage2FuzzyMerchantRef() {
    String batchId = "BATCH_03";
    // Candidate without providerTxnId
    SettlementRecord r1 =
        new SettlementRecord(
            "REC_1",
            "PAY_1",
            "SEL_1",
            null,
            "ORD_SPECIAL_123",
            "CAPTURE",
            150000L,
            3000L,
            147000L,
            Instant.now());

    // Provider line has different providerTxnId but exact merchant_ref
    ProviderLine l1 =
        new ProviderLine(
            UUID.randomUUID(),
            1,
            batchId,
            "ptxn_diff",
            "CAPTURE",
            "ORD_SPECIAL_123",
            null,
            150000L,
            3000L,
            147000L,
            "INR",
            Instant.now(),
            LocalDate.now(),
            null);
    l1.setId(301L);

    when(recordRepo.findByBatchId(batchId)).thenReturn(List.of(r1));
    when(lineRepo.findByProviderBatchId(batchId)).thenReturn(List.of(l1));

    ReconciliationRunResponse response =
        engine.execute(new ReconciliationRunRequest(batchId, null, null, 200, 1L));

    assertThat(response).isNotNull();
    assertThat(response.matchedCount()).isEqualTo(1);
    assertThat(r1.getStatus()).isEqualTo(SettlementRecordStatus.MATCHED);
  }

  @Test
  @DisplayName("Stage 4: Ground truth precision and recall evaluation against manifest")
  void testGroundTruthEvaluation() {
    String batchId = "BATCH_04";
    SettlementRecord r1 =
        new SettlementRecord(
            "REC_1",
            "PAY_1",
            "SEL_1",
            "ptxn_1",
            "ord_1",
            "CAPTURE",
            100000L,
            2000L,
            98000L,
            Instant.now());

    // Provider line with amount mismatch
    ProviderLine l1 =
        new ProviderLine(
            UUID.randomUUID(),
            1,
            batchId,
            "ptxn_1",
            "CAPTURE",
            "ord_1",
            "ord_1",
            105000L,
            2000L,
            103000L,
            "INR",
            Instant.now(),
            LocalDate.now(),
            null);
    l1.setId(401L);

    when(recordRepo.findByBatchId(batchId)).thenReturn(List.of(r1));
    when(lineRepo.findByProviderBatchId(batchId)).thenReturn(List.of(l1));

    SimulationManifest manifest =
        new SimulationManifest(
            batchId, "sim.csv", "FAULT_GROSS_AMOUNT_MISMATCH", "REC_1", "Gross changed");
    when(manifestRepo.findByBatchId(batchId)).thenReturn(List.of(manifest));

    ReconciliationRunResponse response =
        engine.execute(new ReconciliationRunRequest(batchId, null, null, 200, 1L));

    assertThat(response).isNotNull();
    assertThat(response.precisionPct()).isGreaterThan(0.0);
    assertThat(response.recallPct()).isGreaterThan(0.0);
  }
}
