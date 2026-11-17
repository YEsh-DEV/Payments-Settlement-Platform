package io.github.yesh.paycore.identity.repository;

import io.github.yesh.paycore.identity.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  java.util.List<AuditLog> findByActorOrderByCreatedAtDesc(String actor);
}
