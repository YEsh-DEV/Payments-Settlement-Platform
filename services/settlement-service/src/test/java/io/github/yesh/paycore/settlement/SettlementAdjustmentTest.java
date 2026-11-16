package io.github.yesh.paycore.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.yesh.paycore.settlement.client.LedgerClient;
import io.github.yesh.paycore.settlement.domain.*;
import io.github.yesh.paycore.settlement.dto.AdjustmentResponse;
import io.github.yesh.paycore.settlement.dto.ProposeAdjustmentRequest;
import io.github.yesh.paycore.settlement.exception.MakerCheckerViolationException;
import io.github.yesh.paycore.settlement.repository.AdjustmentRepository;
import io.github.yesh.paycore.settlement.repository.CaseNoteRepository;
import io.github.yesh.paycore.settlement.repository.CaseRepository;
import io.github.yesh.paycore.settlement.service.AdjustmentService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementAdjustmentTest {

  private AdjustmentRepository adjustmentRepo;
  private CaseRepository caseRepo;
  private CaseNoteRepository noteRepo;
  private LedgerClient ledgerClient;
  private AdjustmentService adjustmentService;

  @BeforeEach
  void setUp() {
    adjustmentRepo = mock(AdjustmentRepository.class);
    caseRepo = mock(CaseRepository.class);
    noteRepo = mock(CaseNoteRepository.class);
    ledgerClient = mock(LedgerClient.class);
    adjustmentService = new AdjustmentService(adjustmentRepo, caseRepo, noteRepo, ledgerClient);
  }

  @Test
  @DisplayName("Should propose adjustment and transition case to IN_REVIEW")
  void testProposeAdjustment() {
    DiscrepancyCase c =
        new DiscrepancyCase(
            "CASE_FEE",
            "RUN_1",
            "REC_1",
            1L,
            CaseType.FEE_VARIANCE,
            CaseSeverity.LOW,
            200L,
            Instant.now().plusSeconds(3600));

    when(caseRepo.findByCaseId("CASE_FEE")).thenReturn(Optional.of(c));
    when(adjustmentRepo.save(any(AdjustmentRecord.class))).thenAnswer(inv -> inv.getArgument(0));

    AdjustmentResponse response =
        adjustmentService.proposeAdjustment(
            new ProposeAdjustmentRequest(
                "CASE_FEE", "FEE_VARIANCE", 200L, "reconcile provider fee"),
            "analyst_alice");

    assertThat(response).isNotNull();
    assertThat(response.status()).isEqualTo("PROPOSED");
    assertThat(response.proposedBy()).isEqualTo("analyst_alice");
    assertThat(response.debitAccount()).isEqualTo("PROVIDER_FEE_EXPENSE");
    assertThat(response.creditAccount()).isEqualTo("PROVIDER_RECEIVABLE");
    assertThat(c.getStatus()).isEqualTo(CaseStatus.IN_REVIEW);

    verify(caseRepo).save(c);
    verify(noteRepo).save(any());
  }

  @Test
  @DisplayName("Maker-Checker: Should reject when approver equals proposer")
  void testMakerCheckerRejection() {
    AdjustmentRecord adj =
        new AdjustmentRecord(
            "ADJ_1",
            "CASE_1",
            "FEE_VARIANCE",
            "PROVIDER_FEE_EXPENSE",
            "PROVIDER_RECEIVABLE",
            200L,
            "analyst_alice");

    when(adjustmentRepo.findByAdjustmentId("ADJ_1")).thenReturn(Optional.of(adj));

    // Alice attempts to approve her own proposed adjustment
    assertThatThrownBy(() -> adjustmentService.approveAdjustment("ADJ_1", "analyst_alice"))
        .isInstanceOf(MakerCheckerViolationException.class)
        .hasMessageContaining("Maker-checker violation");
  }

  @Test
  @DisplayName(
      "Maker-Checker: Should approve, post to ledger, and resolve case when approver differs")
  void testMakerCheckerApproval() {
    AdjustmentRecord adj =
        new AdjustmentRecord(
            "ADJ_2",
            "CASE_2",
            "FEE_VARIANCE",
            "PROVIDER_FEE_EXPENSE",
            "PROVIDER_RECEIVABLE",
            500L,
            "analyst_alice");

    DiscrepancyCase c =
        new DiscrepancyCase(
            "CASE_2",
            "RUN_1",
            "REC_2",
            2L,
            CaseType.FEE_VARIANCE,
            CaseSeverity.LOW,
            500L,
            Instant.now().plusSeconds(3600));
    c.setStatus(CaseStatus.IN_REVIEW);
    c.setProposedBy("analyst_alice");

    when(adjustmentRepo.findByAdjustmentId("ADJ_2")).thenReturn(Optional.of(adj));
    when(caseRepo.findByCaseId("CASE_2")).thenReturn(Optional.of(c));
    when(adjustmentRepo.save(any(AdjustmentRecord.class))).thenAnswer(inv -> inv.getArgument(0));
    when(ledgerClient.postAdjustment(any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(Map.of("journalId", UUID.randomUUID().toString()));

    // Admin Bob approves Alice's proposed adjustment
    AdjustmentResponse response = adjustmentService.approveAdjustment("ADJ_2", "admin_bob");

    assertThat(response.status()).isEqualTo("POSTED");
    assertThat(response.approvedBy()).isEqualTo("admin_bob");
    assertThat(c.getStatus()).isEqualTo(CaseStatus.RESOLVED);
    assertThat(c.getApprovedBy()).isEqualTo("admin_bob");
    assertThat(c.getResolvedAt()).isNotNull();

    verify(ledgerClient)
        .postAdjustment(
            eq("ADJ_2"),
            eq("PROVIDER_FEE_EXPENSE"),
            eq("PROVIDER_RECEIVABLE"),
            eq(500L),
            eq("CASE_2"),
            any());
    verify(caseRepo).save(c);
  }

  @Test
  @DisplayName("Adjustment ledger call failure: stays APPROVED and scheduled retry posts it later")
  void testApprovalFailureRetainsApprovedStatusAndRetryPosts() {
    AdjustmentRecord adj =
        new AdjustmentRecord(
            "ADJ_FAIL",
            "CASE_FAIL",
            "WRITE_OFF",
            "WRITE_OFF_EXPENSE",
            "PROVIDER_RECEIVABLE",
            1200L,
            "analyst_charlie");

    DiscrepancyCase c =
        new DiscrepancyCase(
            "CASE_FAIL",
            "RUN_1",
            "REC_F",
            10L,
            CaseType.MISSING_IN_PROVIDER,
            CaseSeverity.HIGH,
            1200L,
            Instant.now().plusSeconds(3600));
    c.setStatus(CaseStatus.IN_REVIEW);
    c.setProposedBy("analyst_charlie");

    when(adjustmentRepo.findByAdjustmentId("ADJ_FAIL")).thenReturn(Optional.of(adj));
    when(caseRepo.findByCaseId("CASE_FAIL")).thenReturn(Optional.of(c));
    when(adjustmentRepo.save(any(AdjustmentRecord.class))).thenAnswer(inv -> inv.getArgument(0));

    // Ledger initially down on first call, returns journalId on retry
    when(ledgerClient.postAdjustment(any(), any(), any(), anyLong(), any(), any()))
        .thenThrow(new RuntimeException("Ledger 503 unavailable"))
        .thenReturn(Map.of("journalId", UUID.randomUUID().toString()));

    AdjustmentResponse response = adjustmentService.approveAdjustment("ADJ_FAIL", "admin_bob");

    // Must stay APPROVED (not POSTED) so it's not marked done prematurely
    assertThat(response.status()).isEqualTo("APPROVED");
    assertThat(adj.getStatus()).isEqualTo("APPROVED");
    assertThat(c.getStatus()).isEqualTo(CaseStatus.IN_REVIEW);

    // Later: Scheduled retry job runs when ledger is back online
    when(adjustmentRepo.findByStatus("APPROVED")).thenReturn(List.of(adj));

    int retriedCount = adjustmentService.retryApprovedAdjustments();
    assertThat(retriedCount).isEqualTo(1);
    assertThat(adj.getStatus()).isEqualTo("POSTED");
    assertThat(c.getStatus()).isEqualTo(CaseStatus.WRITTEN_OFF);
  }
}
