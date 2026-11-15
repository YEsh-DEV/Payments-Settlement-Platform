package io.github.yesh.paycore.ledger.controller;

import io.github.yesh.paycore.ledger.dto.CapturePaymentRequest;
import io.github.yesh.paycore.ledger.dto.PaymentResponse;
import io.github.yesh.paycore.ledger.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

  private final PaymentService paymentService;

  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @PostMapping
  public ResponseEntity<PaymentResponse> capturePayment(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CapturePaymentRequest request) {
    PaymentResponse response = paymentService.capturePayment(request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/{paymentId}")
  public ResponseEntity<PaymentResponse> getPayment(@PathVariable String paymentId) {
    return ResponseEntity.ok(paymentService.getPayment(paymentId));
  }
}
