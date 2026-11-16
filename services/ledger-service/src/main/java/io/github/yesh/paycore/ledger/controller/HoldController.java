package io.github.yesh.paycore.ledger.controller;

import io.github.yesh.paycore.ledger.dto.CaptureHoldRequest;
import io.github.yesh.paycore.ledger.dto.CreateHoldRequest;
import io.github.yesh.paycore.ledger.dto.HoldResponse;
import io.github.yesh.paycore.ledger.service.HoldService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/v1/holds", "/holds"})
public class HoldController {

  private final HoldService holdService;

  public HoldController(HoldService holdService) {
    this.holdService = holdService;
  }

  @PostMapping
  public ResponseEntity<HoldResponse> createHold(@Valid @RequestBody CreateHoldRequest request) {
    HoldResponse response = holdService.createHold(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/{holdId}/release")
  public ResponseEntity<HoldResponse> releaseHold(@PathVariable String holdId) {
    return ResponseEntity.ok(holdService.releaseHold(holdId));
  }

  @PostMapping("/{holdId}/capture")
  public ResponseEntity<HoldResponse> captureHold(
      @PathVariable String holdId, @Valid @RequestBody CaptureHoldRequest request) {
    return ResponseEntity.ok(holdService.captureHold(holdId, request));
  }

  @GetMapping("/{holdId}")
  public ResponseEntity<HoldResponse> getHold(@PathVariable String holdId) {
    return ResponseEntity.ok(holdService.getHold(holdId));
  }

  @GetMapping("/account/{accountCode}")
  public ResponseEntity<List<HoldResponse>> getActiveHoldsForAccount(
      @PathVariable String accountCode) {
    return ResponseEntity.ok(holdService.getActiveHoldsForAccount(accountCode));
  }
}
