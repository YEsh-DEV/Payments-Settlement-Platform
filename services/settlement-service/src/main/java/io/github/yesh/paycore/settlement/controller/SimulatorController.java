package io.github.yesh.paycore.settlement.controller;

import io.github.yesh.paycore.settlement.domain.SimulationManifest;
import io.github.yesh.paycore.settlement.dto.SimulationRequest;
import io.github.yesh.paycore.settlement.dto.SimulationResult;
import io.github.yesh.paycore.settlement.service.ProviderSimulatorService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/simulator")
public class SimulatorController {

  private final ProviderSimulatorService simulatorService;

  public SimulatorController(ProviderSimulatorService simulatorService) {
    this.simulatorService = simulatorService;
  }

  @PostMapping("/generate-file")
  public ResponseEntity<SimulationResult> generateFile(
      @RequestBody(required = false) SimulationRequest request) {
    SimulationResult result = simulatorService.generateSettlementFile(request);
    return ResponseEntity.ok(result);
  }

  @GetMapping("/manifests/{batchId}")
  public ResponseEntity<List<SimulationManifest>> getManifests(@PathVariable String batchId) {
    return ResponseEntity.ok(simulatorService.getManifests(batchId));
  }
}
