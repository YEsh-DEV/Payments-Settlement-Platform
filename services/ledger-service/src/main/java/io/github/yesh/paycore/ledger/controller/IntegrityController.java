package io.github.yesh.paycore.ledger.controller;

import io.github.yesh.paycore.ledger.dto.IntegrityReportResponse;
import io.github.yesh.paycore.ledger.service.IntegrityService;
import io.github.yesh.paycore.ledger.strategy.PostingStrategyRouter;
import io.github.yesh.paycore.ledger.strategy.StrategyType;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Controller exposing continuous ledger integrity check endpoint (F-L-09). */
@RestController
@RequestMapping({"/v1/integrity", "/integrity"})
public class IntegrityController {

  private final IntegrityService integrityService;
  private final PostingStrategyRouter strategyRouter;

  public IntegrityController(
      IntegrityService integrityService, PostingStrategyRouter strategyRouter) {
    this.integrityService = integrityService;
    this.strategyRouter = strategyRouter;
  }

  @GetMapping
  public ResponseEntity<IntegrityReportResponse> getIntegrityReport() {
    IntegrityReportResponse report = integrityService.runIntegrityCheck();
    return ResponseEntity.ok(report);
  }

  @GetMapping("/strategy")
  public ResponseEntity<Map<String, String>> getActiveStrategy() {
    return ResponseEntity.ok(Map.of("activeStrategy", strategyRouter.getCurrentStrategy().name()));
  }

  @PostMapping("/strategy")
  public ResponseEntity<Map<String, String>> setStrategy(
      @RequestParam("strategy") String strategy) {
    StrategyType type = StrategyType.valueOf(strategy.toUpperCase());
    strategyRouter.setStrategy(type);
    return ResponseEntity.ok(Map.of("activeStrategy", strategyRouter.getCurrentStrategy().name()));
  }
}
