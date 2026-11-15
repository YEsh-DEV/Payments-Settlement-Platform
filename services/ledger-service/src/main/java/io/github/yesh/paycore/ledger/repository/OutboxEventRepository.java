package io.github.yesh.paycore.ledger.repository;

import io.github.yesh.paycore.ledger.domain.OutboxEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  @Query("SELECT o FROM OutboxEvent o WHERE o.publishedAt IS NULL ORDER BY o.id ASC")
  List<OutboxEvent> findUnpublished(Pageable pageable);
}
