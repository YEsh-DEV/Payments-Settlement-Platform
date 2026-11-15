package io.github.yesh.paycore.settlement.service;

import io.github.yesh.paycore.settlement.domain.CaseNote;
import io.github.yesh.paycore.settlement.domain.CaseSeverity;
import io.github.yesh.paycore.settlement.domain.CaseStatus;
import io.github.yesh.paycore.settlement.domain.DiscrepancyCase;
import io.github.yesh.paycore.settlement.repository.CaseNoteRepository;
import io.github.yesh.paycore.settlement.repository.CaseRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlaEscalationDaemon {

  private static final Logger log = LoggerFactory.getLogger(SlaEscalationDaemon.class);

  private final CaseRepository caseRepository;
  private final CaseNoteRepository noteRepository;
  private final MeterRegistry meterRegistry;

  public SlaEscalationDaemon(CaseRepository caseRepository, CaseNoteRepository noteRepository) {
    this(caseRepository, noteRepository, new SimpleMeterRegistry());
  }

  @Autowired
  public SlaEscalationDaemon(
      CaseRepository caseRepository,
      CaseNoteRepository noteRepository,
      MeterRegistry meterRegistry) {
    this.caseRepository = caseRepository;
    this.noteRepository = noteRepository;
    this.meterRegistry = meterRegistry;
  }

  @Scheduled(cron = "${paycore.cases.escalation-cron:0 * * * * *}")
  @Transactional
  public int sweepAndEscalate() {
    Instant now = Instant.now();
    Set<CaseStatus> terminalStatuses =
        Set.of(CaseStatus.RESOLVED, CaseStatus.WRITTEN_OFF, CaseStatus.CLOSED);

    List<DiscrepancyCase> breachedCases =
        caseRepository.findByStatusNotInAndSlaDeadlineBefore(terminalStatuses, now);

    int escalatedCount = 0;

    for (DiscrepancyCase c : breachedCases) {
      if (c.getStatus() == CaseStatus.ESCALATED) {
        continue;
      }

      CaseSeverity currentSeverity = c.getSeverity();
      CaseSeverity elevatedSeverity =
          switch (currentSeverity) {
            case LOW -> CaseSeverity.MEDIUM;
            case MEDIUM -> CaseSeverity.HIGH;
            case HIGH, CRITICAL -> CaseSeverity.CRITICAL;
          };

      Duration halfSla =
          switch (elevatedSeverity) {
            case CRITICAL -> Duration.ofHours(2);
            case HIGH -> Duration.ofHours(12);
            case MEDIUM -> Duration.ofHours(36);
            case LOW -> Duration.ofHours(84);
          };

      c.setStatus(CaseStatus.ESCALATED);
      c.setSeverity(elevatedSeverity);
      c.setSlaDeadline(now.plus(halfSla));

      caseRepository.save(c);

      noteRepository.save(
          new CaseNote(
              c.getCaseId(),
              "SYSTEM_DAEMON",
              "SLA breached. Auto-escalated status to ESCALATED and severity elevated from "
                  + currentSeverity
                  + " to "
                  + elevatedSeverity));

      log.warn(
          "Case {} auto-escalated to {} with elevated severity {}",
          c.getCaseId(),
          c.getStatus(),
          c.getSeverity());

      meterRegistry.counter("paycore_cases_escalated_total").increment();
      escalatedCount++;
    }

    if (escalatedCount > 0) {
      log.info("SLA Escalation sweep completed: escalated {} cases.", escalatedCount);
    }

    return escalatedCount;
  }
}
