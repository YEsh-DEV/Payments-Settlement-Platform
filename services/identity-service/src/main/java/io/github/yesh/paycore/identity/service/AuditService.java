package io.github.yesh.paycore.identity.service;

import io.github.yesh.paycore.identity.domain.AuditLog;
import io.github.yesh.paycore.identity.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditService {

  private final AuditLogRepository auditLogRepository;

  public AuditService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  public Page<AuditLog> listAuditLogs(Pageable pageable) {
    return auditLogRepository.findAll(pageable);
  }

  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public AuditLog recordAuditLog(
      String actor,
      String action,
      String entity,
      String entityId,
      String details,
      String requestId) {
    String jsonDetails;
    if (details == null) {
      jsonDetails = "{}";
    } else if (details.trim().startsWith("{") || details.trim().startsWith("[")) {
      jsonDetails = details;
    } else {
      jsonDetails = "{\"message\":\"" + details.replace("\"", "\\\"") + "\"}";
    }
    AuditLog entry = new AuditLog(actor, action, entity, entityId, jsonDetails, requestId);
    return auditLogRepository.save(entry);
  }
}
