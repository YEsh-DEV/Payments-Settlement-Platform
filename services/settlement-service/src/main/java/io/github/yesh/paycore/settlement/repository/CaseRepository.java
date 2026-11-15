package io.github.yesh.paycore.settlement.repository;

import io.github.yesh.paycore.settlement.domain.CaseSeverity;
import io.github.yesh.paycore.settlement.domain.CaseStatus;
import io.github.yesh.paycore.settlement.domain.CaseType;
import io.github.yesh.paycore.settlement.domain.DiscrepancyCase;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseRepository extends JpaRepository<DiscrepancyCase, UUID> {

  Optional<DiscrepancyCase> findByCaseId(String caseId);

  List<DiscrepancyCase> findByRunId(String runId);

  List<DiscrepancyCase> findByStatus(CaseStatus status);

  List<DiscrepancyCase> findBySeverity(CaseSeverity severity);

  Optional<DiscrepancyCase> findByTypeAndRecordIdAndProviderLineId(
      CaseType type, String recordId, Long providerLineId);

  List<DiscrepancyCase> findByStatusNotInAndSlaDeadlineBefore(
      Collection<CaseStatus> terminalStatuses, Instant deadline);

  Page<DiscrepancyCase> findAllByOrderByCreatedAtDesc(Pageable pageable);

  Page<DiscrepancyCase> findByStatusOrderByCreatedAtDesc(CaseStatus status, Pageable pageable);
}
