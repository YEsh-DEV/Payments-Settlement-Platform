package io.github.yesh.paycore.settlement.repository;

import io.github.yesh.paycore.settlement.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

  boolean existsByEventId(String eventId);
}
