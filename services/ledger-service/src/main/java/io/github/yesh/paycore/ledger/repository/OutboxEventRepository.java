package io.github.yesh.paycore.ledger.repository;

import io.github.yesh.paycore.ledger.domain.OutboxEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  @Query("SELECT o FROM OutboxEvent o WHERE o.publishedAt IS NULL ORDER BY o.id ASC")
  List<OutboxEvent> findUnpublished(Pageable pageable);

  @Query(
      value =
          "UPDATE outbox_events SET claimed_at = NOW() "
              + "WHERE id IN ("
              + "  SELECT id FROM outbox_events "
              + "  WHERE published_at IS NULL "
              + "    AND (claimed_at IS NULL OR claimed_at < NOW() - INTERVAL '60 seconds') "
              + "  ORDER BY id ASC "
              + "  LIMIT :limit "
              + "  FOR UPDATE SKIP LOCKED"
              + ") RETURNING *",
      nativeQuery = true)
  List<OutboxEvent> claimUnpublishedForUpdate(@Param("limit") int limit);

  @Modifying
  @Query("UPDATE OutboxEvent o SET o.publishedAt = CURRENT_TIMESTAMP WHERE o.id IN :ids")
  int markPublished(@Param("ids") List<Long> ids);
}
