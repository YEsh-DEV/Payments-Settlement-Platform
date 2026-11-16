package io.github.yesh.paycore.ledger.repository;

import io.github.yesh.paycore.ledger.domain.IdempotencyKeyId;
import io.github.yesh.paycore.ledger.domain.IdempotencyRecord;
import io.github.yesh.paycore.ledger.domain.IdempotencyStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, IdempotencyKeyId> {

  default Optional<IdempotencyRecord> findByScopeAndKey(String scope, String key) {
    return findById(new IdempotencyKeyId(scope, key));
  }

  @Modifying
  @Query("UPDATE IdempotencyRecord r SET r.status = :targetStatus WHERE r.status = :sourceStatus")
  int updateStatusByStatus(
      @Param("sourceStatus") IdempotencyStatus sourceStatus,
      @Param("targetStatus") IdempotencyStatus targetStatus);

  @Modifying
  @Query("DELETE FROM IdempotencyRecord r WHERE r.status IN :statuses AND r.createdAt < :cutoff")
  int deleteByStatusInAndCreatedAtBefore(
      @Param("statuses") Collection<IdempotencyStatus> statuses, @Param("cutoff") Instant cutoff);
}
