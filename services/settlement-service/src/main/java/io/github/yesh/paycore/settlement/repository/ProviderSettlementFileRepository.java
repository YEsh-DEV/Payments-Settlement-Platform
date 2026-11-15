package io.github.yesh.paycore.settlement.repository;

import io.github.yesh.paycore.settlement.domain.ProviderSettlementFile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderSettlementFileRepository
    extends JpaRepository<ProviderSettlementFile, UUID> {

  Optional<ProviderSettlementFile> findByFilename(String filename);

  Optional<ProviderSettlementFile> findByFileHash(String fileHash);

  boolean existsByFileHash(String fileHash);

  Page<ProviderSettlementFile> findAllByOrderByIngestedAtDesc(Pageable pageable);
}
