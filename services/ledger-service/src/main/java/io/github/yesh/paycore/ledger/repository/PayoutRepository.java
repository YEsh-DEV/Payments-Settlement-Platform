package io.github.yesh.paycore.ledger.repository;

import io.github.yesh.paycore.ledger.domain.Payout;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, UUID> {

  Optional<Payout> findByPayoutId(String payoutId);

  List<Payout> findByBatchId(String batchId);
}
