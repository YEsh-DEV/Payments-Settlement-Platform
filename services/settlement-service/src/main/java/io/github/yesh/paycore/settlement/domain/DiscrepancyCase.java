package io.github.yesh.paycore.settlement.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cases")
public class DiscrepancyCase {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "case_id", nullable = false, unique = true, length = 64)
  private String caseId;

  @Column(name = "run_id", length = 64)
  private String runId;

  @Column(name = "record_id", length = 64)
  private String recordId;

  @Column(name = "provider_line_id")
  private Long providerLineId;

  @Column(name = "deposit_id", length = 64)
  private String depositId;

  @Column(name = "reference_id", length = 128)
  private String referenceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private CaseType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private CaseSeverity severity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private CaseStatus status = CaseStatus.OPEN;

  @Column(name = "assigned_to", length = 64)
  private String assignedTo;

  @Column(name = "proposed_action", length = 64)
  private String proposedAction;

  @Column(name = "proposed_by", length = 64)
  private String proposedBy;

  @Column(name = "approved_by", length = 64)
  private String approvedBy;

  @Column(name = "variance_paise", nullable = false)
  private long variancePaise = 0L;

  @Column(name = "sla_deadline", nullable = false)
  private Instant slaDeadline;

  @Version
  @Column(nullable = false)
  private Long version = 0L;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  public DiscrepancyCase() {}

  public DiscrepancyCase(
      String caseId,
      String runId,
      String recordId,
      Long providerLineId,
      CaseType type,
      CaseSeverity severity,
      long variancePaise,
      Instant slaDeadline) {
    this.caseId = caseId;
    this.runId = runId;
    this.recordId = recordId;
    this.providerLineId = providerLineId;
    this.type = type;
    this.severity = severity;
    this.variancePaise = variancePaise;
    this.slaDeadline = slaDeadline;
    this.status = CaseStatus.OPEN;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getCaseId() {
    return caseId;
  }

  public String getRunId() {
    return runId;
  }

  public String getRecordId() {
    return recordId;
  }

  public Long getProviderLineId() {
    return providerLineId;
  }

  public String getDepositId() {
    return depositId;
  }

  public void setDepositId(String depositId) {
    this.depositId = depositId;
  }

  public String getReferenceId() {
    return referenceId;
  }

  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }

  public CaseType getType() {
    return type;
  }

  public void setType(CaseType type) {
    this.type = type;
  }

  public CaseSeverity getSeverity() {
    return severity;
  }

  public void setSeverity(CaseSeverity severity) {
    this.severity = severity;
  }

  public CaseStatus getStatus() {
    return status;
  }

  public void setStatus(CaseStatus status) {
    this.status = status;
  }

  public String getAssignedTo() {
    return assignedTo;
  }

  public void setAssignedTo(String assignedTo) {
    this.assignedTo = assignedTo;
  }

  public String getProposedAction() {
    return proposedAction;
  }

  public void setProposedAction(String proposedAction) {
    this.proposedAction = proposedAction;
  }

  public String getProposedBy() {
    return proposedBy;
  }

  public void setProposedBy(String proposedBy) {
    this.proposedBy = proposedBy;
  }

  public String getApprovedBy() {
    return approvedBy;
  }

  public void setApprovedBy(String approvedBy) {
    this.approvedBy = approvedBy;
  }

  public long getVariancePaise() {
    return variancePaise;
  }

  public void setVariancePaise(long variancePaise) {
    this.variancePaise = variancePaise;
  }

  public Instant getSlaDeadline() {
    return slaDeadline;
  }

  public void setSlaDeadline(Instant slaDeadline) {
    this.slaDeadline = slaDeadline;
  }

  public Long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }
}
