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
}
