package io.github.yesh.paycore.settlement.repository;

import io.github.yesh.paycore.settlement.domain.AdjustmentRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdjustmentRepository extends JpaRepository<AdjustmentRecord, UUID> {

  Optional<AdjustmentRecord> findByAdjustmentId(String adjustmentId);

  List<AdjustmentRecord> findByCaseId(String caseId);

  List<AdjustmentRecord> findByStatus(String status);

  Page<AdjustmentRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
