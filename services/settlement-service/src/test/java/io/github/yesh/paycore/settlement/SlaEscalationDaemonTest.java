package io.github.yesh.paycore.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.yesh.paycore.settlement.domain.CaseSeverity;
import io.github.yesh.paycore.settlement.domain.CaseStatus;
import io.github.yesh.paycore.settlement.domain.CaseType;
import io.github.yesh.paycore.settlement.domain.DiscrepancyCase;
import io.github.yesh.paycore.settlement.repository.CaseNoteRepository;
import io.github.yesh.paycore.settlement.repository.CaseRepository;
import io.github.yesh.paycore.settlement.service.SlaEscalationDaemon;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlaEscalationDaemonTest {

  private CaseRepository caseRepo;
  private CaseNoteRepository noteRepo;
  private SlaEscalationDaemon daemon;

  @BeforeEach
  void setUp() {
    caseRepo = mock(CaseRepository.class);
    noteRepo = mock(CaseNoteRepository.class);
    daemon = new SlaEscalationDaemon(caseRepo, noteRepo);
  }

  @Test
  @DisplayName("Should auto-escalate breached cases and elevate severity")
  void testAutoEscalateBreachedCase() {
    Instant pastDeadline = Instant.now().minus(2, ChronoUnit.HOURS);
    DiscrepancyCase c =
        new DiscrepancyCase(
            "CASE_BREACHED",
            "RUN_1",
            "REC_1",
            1L,
            CaseType.AMOUNT_MISMATCH,
            CaseSeverity.MEDIUM,
            25000L,
            pastDeadline);

    when(caseRepo.findByStatusNotInAndSlaDeadlineBefore(any(), any())).thenReturn(List.of(c));

    int escalated = daemon.sweepAndEscalate();

    assertThat(escalated).isEqualTo(1);
    assertThat(c.getStatus()).isEqualTo(CaseStatus.ESCALATED);
    assertThat(c.getSeverity()).isEqualTo(CaseSeverity.HIGH); // Elevated from MEDIUM to HIGH
    assertThat(c.getSlaDeadline()).isAfter(Instant.now()); // Deadline extended

    verify(caseRepo).save(c);
    verify(noteRepo).save(any());
  }
}
