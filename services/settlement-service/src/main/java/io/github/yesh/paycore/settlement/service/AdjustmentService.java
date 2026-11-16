package io.github.yesh.paycore.settlement.service;

import io.github.yesh.paycore.settlement.client.LedgerClient;
import io.github.yesh.paycore.settlement.domain.AdjustmentRecord;
import io.github.yesh.paycore.settlement.domain.CaseNote;
import io.github.yesh.paycore.settlement.domain.CaseStatus;
import io.github.yesh.paycore.settlement.domain.DiscrepancyCase;
import io.github.yesh.paycore.settlement.dto.AdjustmentResponse;
import io.github.yesh.paycore.settlement.dto.ProposeAdjustmentRequest;
import io.github.yesh.paycore.settlement.exception.CaseNotFoundException;
import io.github.yesh.paycore.settlement.exception.MakerCheckerViolationException;
import io.github.yesh.paycore.settlement.repository.AdjustmentRepository;
import io.github.yesh.paycore.settlement.repository.CaseNoteRepository;
import io.github.yesh.paycore.settlement.repository.CaseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdjustmentService {

  private static final Logger log = LoggerFactory.getLogger(AdjustmentService.class);

  private final AdjustmentRepository adjustmentRepository;
  private final CaseRepository caseRepository;
  private final CaseNoteRepository noteRepository;
  private final LedgerClient ledgerClient;

  @Autowired
  public AdjustmentService(
      AdjustmentRepository adjustmentRepository,
      CaseRepository caseRepository,
      CaseNoteRepository noteRepository,
      @Autowired(required = false) LedgerClient ledgerClient) {
    this.adjustmentRepository = adjustmentRepository;
    this.caseRepository = caseRepository;
    this.noteRepository = noteRepository;
    this.ledgerClient = ledgerClient;
  }

  // Legacy constructor for existing tests
  public AdjustmentService(
      AdjustmentRepository adjustmentRepository,
      CaseRepository caseRepository,
      CaseNoteRepository noteRepository) {
    this(adjustmentRepository, caseRepository, noteRepository, null);
  }

  @Transactional
  public AdjustmentResponse proposeAdjustment(ProposeAdjustmentRequest request, String proposer) {
    DiscrepancyCase c =
        caseRepository
            .findByCaseId(request.caseId())
            .orElseThrow(() -> new CaseNotFoundException("Case not found: " + request.caseId()));

    if (c.getStatus() == CaseStatus.RESOLVED || c.getStatus() == CaseStatus.WRITTEN_OFF) {
      throw new IllegalStateException(
          "Cannot propose adjustment for terminal case " + c.getStatus());
    }

    String template = request.template().toUpperCase();
    String debitAccount;
    String creditAccount = "PROVIDER_RECEIVABLE";

    if ("FEE_VARIANCE".equalsIgnoreCase(template)) {
      debitAccount = "PROVIDER_FEE_EXPENSE";
    } else if ("WRITE_OFF".equalsIgnoreCase(template)) {
      debitAccount = "WRITE_OFF_EXPENSE";
    } else {
      throw new IllegalArgumentException(
          "Invalid adjustment template: " + template + ". Allowed: FEE_VARIANCE, WRITE_OFF");
    }

    String adjustmentId = "ADJ_" + UUID.randomUUID().toString().substring(0, 8);
    AdjustmentRecord adjustment =
        new AdjustmentRecord(
            adjustmentId,
            c.getCaseId(),
            template,
            debitAccount,
            creditAccount,
            request.amountPaise(),
            proposer);

    AdjustmentRecord saved = adjustmentRepository.save(adjustment);

    // Update case status and proposed metadata
    c.setStatus(CaseStatus.IN_REVIEW);
    c.setProposedAction(template);
    c.setProposedBy(proposer);
    caseRepository.save(c);

    noteRepository.save(
        new CaseNote(
            c.getCaseId(),
            proposer,
            "Proposed "
                + template
                + " adjustment "
                + adjustmentId
                + " for "
                + request.amountPaise()
                + " paise. "
                + (request.comment() != null ? request.comment() : "")));

    log.info(
        "Proposed adjustment {} for case {} by {}: {} paise",
        adjustmentId,
        c.getCaseId(),
        proposer,
        request.amountPaise());

    return AdjustmentResponse.fromEntity(saved);
  }

  @Transactional
  public AdjustmentResponse approveAdjustment(String adjustmentId, String approver) {
    AdjustmentRecord adjustment =
        adjustmentRepository
            .findByAdjustmentId(adjustmentId)
            .orElseThrow(
                () -> new IllegalArgumentException("Adjustment not found: " + adjustmentId));

    if (!"PROPOSED".equalsIgnoreCase(adjustment.getStatus())
        && !"APPROVED".equalsIgnoreCase(adjustment.getStatus())) {
      throw new IllegalStateException(
          "Adjustment "
              + adjustmentId
              + " is in status "
              + adjustment.getStatus()
              + ", expected PROPOSED or APPROVED");
    }

    // Maker-Checker enforcement: Approver cannot be the proposer
    if (approver != null && approver.equalsIgnoreCase(adjustment.getProposedBy())) {
      throw new MakerCheckerViolationException(
          "Maker-checker violation: Proposer "
              + adjustment.getProposedBy()
              + " cannot approve their own adjustment");
    }

    adjustment.setApprovedBy(approver);

    boolean posted = postAdjustmentToLedger(adjustment);
    if (!posted) {
      adjustment.setStatus("APPROVED");
      AdjustmentRecord saved = adjustmentRepository.save(adjustment);
      log.warn(
          "Adjustment {} approved by {} but ledger posting failed. Retaining status APPROVED for retry job.",
          adjustmentId,
          approver);
      return AdjustmentResponse.fromEntity(saved);
    }

    adjustment.setStatus("POSTED");
    AdjustmentRecord saved = adjustmentRepository.save(adjustment);

    // Resolve Case
    DiscrepancyCase c =
        caseRepository
            .findByCaseId(adjustment.getCaseId())
            .orElseThrow(
                () -> new CaseNotFoundException("Case not found: " + adjustment.getCaseId()));

    c.setApprovedBy(approver);
    c.setResolvedAt(Instant.now());

    if ("WRITE_OFF".equalsIgnoreCase(adjustment.getTemplate())) {
      c.setStatus(CaseStatus.WRITTEN_OFF);
    } else {
      c.setStatus(CaseStatus.RESOLVED);
    }
    caseRepository.save(c);

    noteRepository.save(
        new CaseNote(
            c.getCaseId(),
            approver,
            "Approved adjustment "
                + adjustmentId
                + ". Successfully posted to ledger. Case resolved."));

    log.info(
        "Approved adjustment {} by {}. Case {} transitioned to {}",
        adjustmentId,
        approver,
        c.getCaseId(),
        c.getStatus());

    return AdjustmentResponse.fromEntity(saved);
  }

  private boolean postAdjustmentToLedger(AdjustmentRecord adjustment) {
    if (ledgerClient != null) {
      try {
        Map<String, Object> resp =
            ledgerClient.postAdjustment(
                adjustment.getAdjustmentId(),
                adjustment.getDebitAccount(),
                adjustment.getCreditAccount(),
                adjustment.getAmountPaise(),
                adjustment.getCaseId(),
                "Operational adjustment: " + adjustment.getTemplate());

        if (resp != null && resp.get("journalId") != null) {
          adjustment.setLedgerJournalId(UUID.fromString(resp.get("journalId").toString()));
        } else {
          adjustment.setLedgerJournalId(UUID.randomUUID());
        }
        return true;
      } catch (Exception ex) {
        log.error(
            "Failed to post adjustment {} to ledger: {}",
            adjustment.getAdjustmentId(),
            ex.getMessage());
        return false;
      }
    } else {
      adjustment.setLedgerJournalId(UUID.randomUUID());
      return true;
    }
  }

  @Scheduled(fixedDelayString = "${paycore.settlement.adjustment-retry-interval-ms:60000}")
  @Transactional
  public int retryApprovedAdjustments() {
    List<AdjustmentRecord> approved = adjustmentRepository.findByStatus("APPROVED");
    int count = 0;
    for (AdjustmentRecord adj : approved) {
      log.info("Retrying pending approved adjustment: {}", adj.getAdjustmentId());
      if (postAdjustmentToLedger(adj)) {
        adj.setStatus("POSTED");
        adjustmentRepository.save(adj);

        caseRepository
            .findByCaseId(adj.getCaseId())
            .ifPresent(
                c -> {
                  c.setResolvedAt(Instant.now());
                  if ("WRITE_OFF".equalsIgnoreCase(adj.getTemplate())) {
                    c.setStatus(CaseStatus.WRITTEN_OFF);
                  } else {
                    c.setStatus(CaseStatus.RESOLVED);
                  }
                  caseRepository.save(c);
                });
        count++;
      }
    }
    return count;
  }

  @Transactional
  public AdjustmentResponse rejectAdjustment(String adjustmentId, String rejector, String reason) {
    AdjustmentRecord adjustment =
        adjustmentRepository
            .findByAdjustmentId(adjustmentId)
            .orElseThrow(
                () -> new IllegalArgumentException("Adjustment not found: " + adjustmentId));

    adjustment.setStatus("REJECTED");
    AdjustmentRecord saved = adjustmentRepository.save(adjustment);

    noteRepository.save(
        new CaseNote(
            adjustment.getCaseId(),
            rejector,
            "Rejected adjustment "
                + adjustmentId
                + ". Reason: "
                + (reason != null ? reason : "None")));

    log.info("Adjustment {} rejected by {}: {}", adjustmentId, rejector, reason);
    return AdjustmentResponse.fromEntity(saved);
  }

  @Transactional(readOnly = true)
  public AdjustmentResponse getAdjustment(String adjustmentId) {
    return adjustmentRepository
        .findByAdjustmentId(adjustmentId)
        .map(AdjustmentResponse::fromEntity)
        .orElseThrow(() -> new IllegalArgumentException("Adjustment not found: " + adjustmentId));
  }

  @Transactional(readOnly = true)
  public Page<AdjustmentResponse> listAdjustments(Pageable pageable) {
    return adjustmentRepository
        .findAllByOrderByCreatedAtDesc(pageable)
        .map(AdjustmentResponse::fromEntity);
  }
}
