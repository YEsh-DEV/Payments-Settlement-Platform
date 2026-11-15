package io.github.yesh.paycore.settlement.repository;

import io.github.yesh.paycore.settlement.domain.SettlementRecord;
import io.github.yesh.paycore.settlement.domain.SettlementRecordStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, UUID> {

  Optional<SettlementRecord> findByRecordId(String recordId);

  List<SettlementRecord> findByBatchId(String batchId);

  List<SettlementRecord> findByStatus(SettlementRecordStatus status);

  Optional<SettlementRecord> findByPaymentId(String paymentId);

  List<SettlementRecord> findByProviderTxnId(String providerTxnId);

  List<SettlementRecord> findByOrderId(String orderId);

  List<SettlementRecord> findByBatchIdIsNullAndOccurredAtLessThanEqual(Instant cutoffTime);

  List<SettlementRecord> findByBatchIdIsNull();

  Page<SettlementRecord> findByBatchId(String batchId, Pageable pageable);

  int countByBatchId(String batchId);
}
