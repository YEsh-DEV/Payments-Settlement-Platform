package io.github.yesh.paycore.settlement.repository;

import io.github.yesh.paycore.settlement.domain.ProviderLine;
import io.github.yesh.paycore.settlement.domain.ProviderLineStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderLineRepository extends JpaRepository<ProviderLine, Long> {

  List<ProviderLine> findByFileId(UUID fileId);

  List<ProviderLine> findByFileIdAndStatus(UUID fileId, ProviderLineStatus status);

  Optional<ProviderLine> findByProviderTxnId(String providerTxnId);

  List<ProviderLine> findAllByProviderTxnId(String providerTxnId);

  List<ProviderLine> findByMerchantRef(String merchantRef);

  List<ProviderLine> findByInternalReference(String internalReference);

  List<ProviderLine> findByProviderBatchId(String providerBatchId);

  List<ProviderLine> findBySettleDateBetween(LocalDate from, LocalDate to);

  Page<ProviderLine> findByFileId(UUID fileId, Pageable pageable);
}
