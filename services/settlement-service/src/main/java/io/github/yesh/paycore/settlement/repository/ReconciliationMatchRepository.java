package io.github.yesh.paycore.settlement.repository;

import io.github.yesh.paycore.settlement.domain.ReconciliationMatch;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReconciliationMatchRepository extends JpaRepository<ReconciliationMatch, Long> {

  List<ReconciliationMatch> findByRunId(String runId);

  List<ReconciliationMatch> findByRecordId(String recordId);

  Page<ReconciliationMatch> findByRunId(String runId, Pageable pageable);
}
