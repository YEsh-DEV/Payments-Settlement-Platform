package io.github.yesh.paycore.ledger.controller;

import io.github.yesh.paycore.ledger.dto.AccountResponse;
import io.github.yesh.paycore.ledger.dto.StatementResponse;
import io.github.yesh.paycore.ledger.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/v1/accounts", "/accounts"})
public class AccountController {

  private final AccountService accountService;

  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @GetMapping("/{code}")
  public ResponseEntity<AccountResponse> getAccount(@PathVariable String code) {
    return ResponseEntity.ok(accountService.getAccount(code));
  }

  @GetMapping("/{code}/statement")
  public ResponseEntity<StatementResponse> getStatement(
      @PathVariable String code,
      @RequestParam(required = false) Long cursor,
      @RequestParam(defaultValue = "50") int limit) {
    return ResponseEntity.ok(accountService.getStatement(code, cursor, limit));
  }
}
