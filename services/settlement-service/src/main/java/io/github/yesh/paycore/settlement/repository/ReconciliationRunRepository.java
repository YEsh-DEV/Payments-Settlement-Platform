package io.github.yesh.paycore.settlement.repository;

import io.github.yesh.paycore.settlement.domain.ReconciliationRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {

  Optional<ReconciliationRun> findByRunId(String runId);

  Optional<ReconciliationRun> findByBatchId(String batchId);

  Page<ReconciliationRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
