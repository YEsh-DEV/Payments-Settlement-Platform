package io.github.yesh.paycore.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.settlement.client.LedgerClient;
import io.github.yesh.paycore.settlement.domain.*;
import io.github.yesh.paycore.settlement.dto.ReconciliationRunRequest;
import io.github.yesh.paycore.settlement.dto.ReconciliationRunResponse;
import io.github.yesh.paycore.settlement.reconciliation.ReconciliationEngine;
import io.github.yesh.paycore.settlement.repository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReconciliationStage4PostingTest {

  private ReconciliationRunRepository runRepo;
  private ReconciliationMatchRepository matchRepo;
  private SettlementRecordRepository recordRepo;
  private ProviderLineRepository lineRepo;
  private BatchRepository batchRepo;
  private BankDepositRepository depositRepo;
  private CaseRepository caseRepo;
  private SimulationManifestRepository manifestRepo;
  private LedgerClient ledgerClient;
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
    ledgerClient = mock(LedgerClient.class);

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
            new ObjectMapper(),
            new SimpleMeterRegistry());
    engine.setLedgerClient(ledgerClient);
  }

  @Test
  @DisplayName("Stage 4: Matched deposit calls ledger POST /v1/provider-deposits with key deposit:<id>")
  void testStage4MatchedDepositPostsToLedger() {
    String batchId = "BATCH_DEP_1";
    Batch batch = new Batch(batchId, "2026-11-16T00:00:00Z");
    batch.setTotalCount(5);
    batch.setTotalGrossPaise(100000L);
    batch.setTotalFeePaise(2000L);
    batch.setTotalNetPaise(98000L);

    BankDeposit deposit =
        new BankDeposit("DEP_101", LocalDate.now(), 98000L, "INR", "RAZORPAY_BATCH_DEP_1");

    when(runRepo.save(any(ReconciliationRun.class))).thenAnswer(inv -> inv.getArgument(0));
    when(recordRepo.findAll()).thenReturn(List.of());
    when(lineRepo.findAll()).thenReturn(List.of());
    when(depositRepo.findAll()).thenReturn(List.of(deposit));
    when(depositRepo.findByStatus("UNMATCHED")).thenReturn(List.of(deposit));
    when(batchRepo.findAll()).thenReturn(List.of(batch));
    when(batchRepo.findByStatus(BatchStatus.COMPLETED)).thenReturn(List.of(batch));
    when(batchRepo.findByBatchId(batchId)).thenReturn(Optional.of(batch));

    ReconciliationRunResponse response = engine.execute(new ReconciliationRunRequest(null, null, null, 200, 1L));

    assertThat(deposit.getStatus()).isEqualTo("MATCHED");
    assertThat(batch.getStatus()).isEqualTo(BatchStatus.RECONCILED);

    // Verify ledger client call
    verify(ledgerClient)
        .postProviderDeposit(
            eq("DEP_101"),
            eq(98000L),
            eq(2000L),
            eq("RAZORPAY_BATCH_DEP_1"),
            any());
  }

  @Test
  @DisplayName("Stage 4: Deposit with no match raises DEPOSIT_UNMATCHED case")
  void testUnmatchedDepositRaisesCase() {
    BankDeposit deposit =
        new BankDeposit("DEP_GHOST", LocalDate.now(), 500000L, "INR", "UNKNOWN_ORIGIN");

    when(runRepo.save(any(ReconciliationRun.class))).thenAnswer(inv -> inv.getArgument(0));
    when(recordRepo.findAll()).thenReturn(List.of());
    when(lineRepo.findAll()).thenReturn(List.of());
    when(depositRepo.findAll()).thenReturn(List.of(deposit));
    when(depositRepo.findByStatus("UNMATCHED")).thenReturn(List.of(deposit));
    when(batchRepo.findAll()).thenReturn(List.of()); // No batches exist!
    when(batchRepo.findByStatus(BatchStatus.COMPLETED)).thenReturn(List.of());

    ReconciliationRunResponse response = engine.execute(new ReconciliationRunRequest(null, null, null, 200, 1L));

    assertThat(deposit.getStatus()).isEqualTo("UNMATCHED");
    verify(ledgerClient, never()).postProviderDeposit(any(), anyLong(), anyLong(), any(), any());

    ArgumentCaptor<List<DiscrepancyCase>> caseCaptor = ArgumentCaptor.forClass(List.class);
    verify(caseRepo).saveAll(caseCaptor.capture());

    List<DiscrepancyCase> capturedCases = caseCaptor.getValue();
    assertThat(capturedCases).hasSize(1);
    DiscrepancyCase c = capturedCases.get(0);
    assertThat(c.getType()).isEqualTo(CaseType.DEPOSIT_UNMATCHED);
    assertThat(c.getSeverity()).isEqualTo(CaseSeverity.HIGH);
    assertThat(c.getVariancePaise()).isEqualTo(500000L);
  }
}
