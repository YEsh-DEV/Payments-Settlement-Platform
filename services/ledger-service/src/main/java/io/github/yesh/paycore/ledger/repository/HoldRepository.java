package io.github.yesh.paycore.ledger.repository;

import io.github.yesh.paycore.ledger.domain.Hold;
import io.github.yesh.paycore.ledger.domain.HoldStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HoldRepository extends JpaRepository<Hold, UUID> {

  Optional<Hold> findByHoldId(String holdId);

  List<Hold> findByAccountIdAndStatus(UUID accountId, HoldStatus status);

  List<Hold> findByStatusAndExpiresAtBefore(HoldStatus status, Instant now);
}
