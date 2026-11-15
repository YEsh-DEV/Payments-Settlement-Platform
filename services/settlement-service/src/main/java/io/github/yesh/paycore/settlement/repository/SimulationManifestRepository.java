package io.github.yesh.paycore.settlement.repository;

import io.github.yesh.paycore.settlement.domain.SimulationManifest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SimulationManifestRepository extends JpaRepository<SimulationManifest, UUID> {

  List<SimulationManifest> findByBatchId(String batchId);

  List<SimulationManifest> findByFilename(String filename);
}
