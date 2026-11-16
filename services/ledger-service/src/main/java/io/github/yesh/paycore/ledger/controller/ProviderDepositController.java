package io.github.yesh.paycore.ledger.controller;

import io.github.yesh.paycore.ledger.dto.CreateProviderDepositRequest;
import io.github.yesh.paycore.ledger.dto.ProviderDepositResponse;
import io.github.yesh.paycore.ledger.service.ProviderDepositService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/v1/provider-deposits", "/provider-deposits"})
public class ProviderDepositController {

  private final ProviderDepositService providerDepositService;

  public ProviderDepositController(ProviderDepositService providerDepositService) {
    this.providerDepositService = providerDepositService;
  }

  @PostMapping
  public ResponseEntity<ProviderDepositResponse> postDeposit(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateProviderDepositRequest request) {
    ProviderDepositResponse response = providerDepositService.postDeposit(request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
