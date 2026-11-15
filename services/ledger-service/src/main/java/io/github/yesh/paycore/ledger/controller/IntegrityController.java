package io.github.yesh.paycore.ledger.controller;

import io.github.yesh.paycore.ledger.dto.IntegrityReportResponse;
import io.github.yesh.paycore.ledger.service.IntegrityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing continuous ledger integrity check endpoint (F-L-09).
 */
@RestController
@RequestMapping({"/v1/integrity", "/integrity"})
public class IntegrityController {

  private final IntegrityService integrityService;

  public IntegrityController(IntegrityService integrityService) {
    this.integrityService = integrityService;
  }

  @GetMapping
  public ResponseEntity<IntegrityReportResponse> getIntegrityReport() {
    IntegrityReportResponse report = integrityService.runIntegrityCheck();
    return ResponseEntity.ok(report);
  }
}
