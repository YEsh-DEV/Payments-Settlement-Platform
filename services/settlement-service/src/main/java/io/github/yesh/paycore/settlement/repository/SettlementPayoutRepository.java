package io.github.yesh.paycore.settlement.repository;

import io.github.yesh.paycore.settlement.domain.SettlementPayout;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SettlementPayoutRepository extends JpaRepository<SettlementPayout, UUID> {

  Optional<SettlementPayout> findByPayoutId(String payoutId);

  List<SettlementPayout> findByBatchId(String batchId);

  List<SettlementPayout> findBySellerId(String sellerId);

  Page<SettlementPayout> findByBatchId(String batchId, Pageable pageable);
}
