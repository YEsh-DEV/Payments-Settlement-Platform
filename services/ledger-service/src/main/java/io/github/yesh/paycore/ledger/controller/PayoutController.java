package io.github.yesh.paycore.ledger.controller;

import io.github.yesh.paycore.ledger.dto.InitiatePayoutRequest;
import io.github.yesh.paycore.ledger.dto.PayoutResponse;
import io.github.yesh.paycore.ledger.service.PayoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payouts")
public class PayoutController {

  private final PayoutService payoutService;

  public PayoutController(PayoutService payoutService) {
    this.payoutService = payoutService;
  }

  @PostMapping
  public ResponseEntity<PayoutResponse> initiatePayout(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody InitiatePayoutRequest request) {
    PayoutResponse response = payoutService.initiatePayout(request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/{payoutId}/confirm")
  public ResponseEntity<PayoutResponse> confirmPayout(
      @PathVariable String payoutId,
      @RequestParam(required = false, defaultValue = "batch-default") String batchId) {
    return ResponseEntity.ok(payoutService.confirmPayout(payoutId, batchId));
  }

  @PostMapping("/{payoutId}/fail")
  public ResponseEntity<PayoutResponse> failPayout(
      @PathVariable String payoutId,
      @RequestParam(required = false, defaultValue = "Bank transfer failed") String reason) {
    return ResponseEntity.ok(payoutService.failPayout(payoutId, reason));
  }

  @GetMapping("/{payoutId}")
  public ResponseEntity<PayoutResponse> getPayout(@PathVariable String payoutId) {
    return ResponseEntity.ok(payoutService.getPayout(payoutId));
  }
}
