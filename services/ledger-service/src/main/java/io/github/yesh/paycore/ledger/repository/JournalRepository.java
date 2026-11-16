package io.github.yesh.paycore.ledger.repository;

import io.github.yesh.paycore.ledger.domain.Journal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JournalRepository extends JpaRepository<Journal, UUID> {

  Optional<Journal> findByIdempotencyKey(String idempotencyKey);

  Optional<Journal> findByScopeAndIdempotencyKey(String scope, String idempotencyKey);

  Optional<Journal> findByReferenceId(String referenceId);
}
