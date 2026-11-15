package io.github.yesh.paycore.settlement.repository;

import io.github.yesh.paycore.settlement.domain.Batch;
import io.github.yesh.paycore.settlement.domain.BatchStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchRepository extends JpaRepository<Batch, UUID> {

  Optional<Batch> findByBatchId(String batchId);

  List<Batch> findByStatus(BatchStatus status);

  Optional<Batch> findFirstByStatusOrderByCreatedAtDesc(BatchStatus status);

  Page<Batch> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
