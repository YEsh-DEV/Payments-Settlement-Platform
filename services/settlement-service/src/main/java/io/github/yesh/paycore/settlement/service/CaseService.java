package io.github.yesh.paycore.settlement.service;

import io.github.yesh.paycore.settlement.domain.CaseNote;
import io.github.yesh.paycore.settlement.domain.CaseStatus;
import io.github.yesh.paycore.settlement.domain.DiscrepancyCase;
import io.github.yesh.paycore.settlement.dto.CaseNoteResponse;
import io.github.yesh.paycore.settlement.dto.CaseResponse;
import io.github.yesh.paycore.settlement.exception.CaseConflictException;
import io.github.yesh.paycore.settlement.exception.CaseNotFoundException;
import io.github.yesh.paycore.settlement.exception.InvalidTransitionException;
import io.github.yesh.paycore.settlement.repository.CaseNoteRepository;
import io.github.yesh.paycore.settlement.repository.CaseRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseService {

  private static final Logger log = LoggerFactory.getLogger(CaseService.class);

  private final CaseRepository caseRepository;
  private final CaseNoteRepository noteRepository;

  public CaseService(CaseRepository caseRepository, CaseNoteRepository noteRepository) {
    this.caseRepository = caseRepository;
    this.noteRepository = noteRepository;
  }

  @Transactional(readOnly = true)
  public CaseResponse getCase(String caseId) {
    return caseRepository
        .findByCaseId(caseId)
        .map(CaseResponse::fromEntity)
        .orElseThrow(() -> new CaseNotFoundException("Case not found: " + caseId));
  }

  @Transactional(readOnly = true)
  public Page<CaseResponse> listCases(CaseStatus status, Pageable pageable) {
    if (status != null) {
      return caseRepository
          .findByStatusOrderByCreatedAtDesc(status, pageable)
          .map(CaseResponse::fromEntity);
    }
    return caseRepository.findAllByOrderByCreatedAtDesc(pageable).map(CaseResponse::fromEntity);
  }

  @Transactional
  public CaseResponse assignCase(
      String caseId, String assignedTo, Long expectedVersion, String actor) {
    DiscrepancyCase c =
        caseRepository
            .findByCaseId(caseId)
            .orElseThrow(() -> new CaseNotFoundException("Case not found: " + caseId));

    if (expectedVersion != null && !expectedVersion.equals(c.getVersion())) {
      throw new CaseConflictException(
          "Case " + caseId + " was modified by another operator (optimistic lock conflict)");
    }

    c.setAssignedTo(assignedTo);
    DiscrepancyCase saved = caseRepository.save(c);

    noteRepository.save(new CaseNote(caseId, actor, "Assigned case to " + assignedTo));

    log.info("Case {} assigned to {} by {}", caseId, assignedTo, actor);
    return CaseResponse.fromEntity(saved);
  }

  @Transactional
  public CaseResponse transitionStatus(
      String caseId, CaseStatus newStatus, Long expectedVersion, String actor, String comment) {
    DiscrepancyCase c =
        caseRepository
            .findByCaseId(caseId)
            .orElseThrow(() -> new CaseNotFoundException("Case not found: " + caseId));

    if (expectedVersion != null && !expectedVersion.equals(c.getVersion())) {
      throw new CaseConflictException(
          "Case " + caseId + " was modified concurrently (optimistic lock conflict)");
    }

    CaseStatus current = c.getStatus();

    if (current == CaseStatus.RESOLVED || current == CaseStatus.WRITTEN_OFF) {
      throw new InvalidTransitionException("Cannot transition from terminal state " + current);
    }

    // Validation rules
    if (newStatus == CaseStatus.IN_REVIEW && c.getAssignedTo() == null) {
      throw new InvalidTransitionException("Starting work requires an assignee");
    }

    boolean valid =
        switch (current) {
          case OPEN -> (newStatus == CaseStatus.IN_REVIEW || newStatus == CaseStatus.ESCALATED);
          case IN_REVIEW ->
              (newStatus == CaseStatus.RESOLVED
                  || newStatus == CaseStatus.WRITTEN_OFF
                  || newStatus == CaseStatus.ESCALATED);
          case ESCALATED ->
              (newStatus == CaseStatus.IN_REVIEW
                  || newStatus == CaseStatus.RESOLVED
                  || newStatus == CaseStatus.WRITTEN_OFF);
          case INVESTIGATING, CLOSED -> true;
          default -> false;
        };

    if (!valid) {
      throw new InvalidTransitionException(
          "Invalid state transition from " + current + " to " + newStatus);
    }

    c.setStatus(newStatus);
    if (newStatus == CaseStatus.RESOLVED || newStatus == CaseStatus.WRITTEN_OFF) {
      c.setResolvedAt(Instant.now());
    }

    DiscrepancyCase saved = caseRepository.save(c);

    String noteText = "Status changed from " + current + " to " + newStatus;
    if (comment != null && !comment.isBlank()) {
      noteText += ": " + comment;
    }
    noteRepository.save(new CaseNote(caseId, actor, noteText));

    log.info("Case {} transitioned from {} to {} by {}", caseId, current, newStatus, actor);
    return CaseResponse.fromEntity(saved);
  }

  @Transactional
  public CaseNoteResponse addNote(String caseId, String author, String content) {
    if (!caseRepository.findByCaseId(caseId).isPresent()) {
      throw new CaseNotFoundException("Case not found: " + caseId);
    }
    CaseNote note = noteRepository.save(new CaseNote(caseId, author, content));
    return CaseNoteResponse.fromEntity(note);
  }

  @Transactional(readOnly = true)
  public List<CaseNoteResponse> getNotes(String caseId) {
    return noteRepository.findByCaseIdOrderByCreatedAtAsc(caseId).stream()
        .map(CaseNoteResponse::fromEntity)
        .toList();
  }
}
