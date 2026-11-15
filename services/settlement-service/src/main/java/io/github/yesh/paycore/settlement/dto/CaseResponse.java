package io.github.yesh.paycore.settlement.dto;

import io.github.yesh.paycore.settlement.domain.CaseSeverity;
import io.github.yesh.paycore.settlement.domain.CaseStatus;
import io.github.yesh.paycore.settlement.domain.CaseType;
import io.github.yesh.paycore.settlement.domain.DiscrepancyCase;
import java.time.Instant;
import java.util.UUID;

public record CaseResponse(
    UUID id,
    String caseId,
    String runId,
    String recordId,
    Long providerLineId,
    String depositId,
    CaseType type,
    CaseSeverity severity,
    CaseStatus status,
    String assignedTo,
    String proposedAction,
    String proposedBy,
    String approvedBy,
    long variancePaise,
    Instant slaDeadline,
    Long version,
    Instant createdAt,
    Instant resolvedAt) {

  public static CaseResponse fromEntity(DiscrepancyCase c) {
    return new CaseResponse(
        c.getId(),
        c.getCaseId(),
        c.getRunId(),
        c.getRecordId(),
        c.getProviderLineId(),
        c.getDepositId(),
        c.getType(),
        c.getSeverity(),
        c.getStatus(),
        c.getAssignedTo(),
        c.getProposedAction(),
        c.getProposedBy(),
        c.getApprovedBy(),
        c.getVariancePaise(),
        c.getSlaDeadline(),
        c.getVersion(),
        c.getCreatedAt(),
        c.getResolvedAt());
  }
}
