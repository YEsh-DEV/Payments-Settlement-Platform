package io.github.yesh.paycore.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.yesh.paycore.settlement.domain.CaseSeverity;
import io.github.yesh.paycore.settlement.domain.CaseStatus;
import io.github.yesh.paycore.settlement.domain.CaseType;
import io.github.yesh.paycore.settlement.domain.DiscrepancyCase;
import io.github.yesh.paycore.settlement.dto.CaseResponse;
import io.github.yesh.paycore.settlement.exception.CaseConflictException;
import io.github.yesh.paycore.settlement.exception.InvalidTransitionException;
import io.github.yesh.paycore.settlement.repository.CaseNoteRepository;
import io.github.yesh.paycore.settlement.repository.CaseRepository;
import io.github.yesh.paycore.settlement.service.CaseService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CaseServiceTest {

  private CaseRepository caseRepo;
  private CaseNoteRepository noteRepo;
  private CaseService caseService;

  @BeforeEach
  void setUp() {
    caseRepo = mock(CaseRepository.class);
    noteRepo = mock(CaseNoteRepository.class);
    caseService = new CaseService(caseRepo, noteRepo);
  }

  @Test
  @DisplayName("Should assign case and record audit note")
  void testAssignCase() {
    DiscrepancyCase c =
        new DiscrepancyCase(
            "CASE_1",
            "RUN_1",
            "REC_1",
            1L,
            CaseType.AMOUNT_MISMATCH,
            CaseSeverity.HIGH,
            5000L,
            Instant.now().plusSeconds(3600));

    when(caseRepo.findByCaseId("CASE_1")).thenReturn(Optional.of(c));
    when(caseRepo.save(any(DiscrepancyCase.class))).thenAnswer(inv -> inv.getArgument(0));

    CaseResponse response = caseService.assignCase("CASE_1", "analyst_bob", 0L, "admin_alice");

    assertThat(response.assignedTo()).isEqualTo("analyst_bob");
    verify(caseRepo).save(any(DiscrepancyCase.class));
    verify(noteRepo).save(any());
  }

  @Test
  @DisplayName("Should detect optimistic locking conflict on version mismatch")
  void testOptimisticLockConflict() {
    DiscrepancyCase c =
        new DiscrepancyCase(
            "CASE_2",
            "RUN_1",
            "REC_1",
            1L,
            CaseType.AMOUNT_MISMATCH,
            CaseSeverity.HIGH,
            5000L,
            Instant.now().plusSeconds(3600));

    when(caseRepo.findByCaseId("CASE_2")).thenReturn(Optional.of(c));

    // Expected version 5 vs current version 0
    assertThatThrownBy(() -> caseService.assignCase("CASE_2", "analyst_bob", 5L, "admin_alice"))
        .isInstanceOf(CaseConflictException.class)
        .hasMessageContaining("optimistic lock conflict");
  }

  @Test
  @DisplayName("Should reject starting work without an assignee")
  void testRejectInReviewWithoutAssignee() {
    DiscrepancyCase c =
        new DiscrepancyCase(
            "CASE_3",
            "RUN_1",
            "REC_1",
            1L,
            CaseType.AMOUNT_MISMATCH,
            CaseSeverity.HIGH,
            5000L,
            Instant.now().plusSeconds(3600));
    // assignedTo is null

    when(caseRepo.findByCaseId("CASE_3")).thenReturn(Optional.of(c));

    assertThatThrownBy(
            () ->
                caseService.transitionStatus(
                    "CASE_3", CaseStatus.IN_REVIEW, 0L, "operator", "start review"))
        .isInstanceOf(InvalidTransitionException.class)
        .hasMessageContaining("Starting work requires an assignee");
  }

  @Test
  @DisplayName("Should reject transition from terminal state")
  void testRejectFromTerminal() {
    DiscrepancyCase c =
        new DiscrepancyCase(
            "CASE_4",
            "RUN_1",
            "REC_1",
            1L,
            CaseType.AMOUNT_MISMATCH,
            CaseSeverity.HIGH,
            5000L,
            Instant.now().plusSeconds(3600));
    c.setStatus(CaseStatus.RESOLVED);

    when(caseRepo.findByCaseId("CASE_4")).thenReturn(Optional.of(c));

    assertThatThrownBy(
            () ->
                caseService.transitionStatus(
                    "CASE_4", CaseStatus.IN_REVIEW, 0L, "operator", "reopen"))
        .isInstanceOf(InvalidTransitionException.class)
        .hasMessageContaining("Cannot transition from terminal state");
  }
}
