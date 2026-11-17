package io.github.yesh.paycore.identity.controller;

import io.github.yesh.paycore.identity.domain.AuditLog;
import io.github.yesh.paycore.identity.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/admin/audit", "/v1/audit"})
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class AuditAdminController {

  private final AuditService auditService;

  public AuditAdminController(AuditService auditService) {
    this.auditService = auditService;
  }

  @GetMapping
  public ResponseEntity<Page<AuditLog>> listAuditLogs(
      @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(auditService.listAuditLogs(pageable));
  }
}
