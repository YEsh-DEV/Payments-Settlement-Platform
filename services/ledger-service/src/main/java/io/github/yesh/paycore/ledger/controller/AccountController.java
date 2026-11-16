package io.github.yesh.paycore.ledger.controller;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.ledger.dto.AccountResponse;
import io.github.yesh.paycore.ledger.dto.StatementResponse;
import io.github.yesh.paycore.ledger.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/v1/accounts", "/accounts"})
public class AccountController {

  private final AccountService accountService;

  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @GetMapping("/{code}")
  public ResponseEntity<AccountResponse> getAccount(
      @PathVariable String code, Authentication authentication) {
    validateAccountAccess(code, authentication);
    return ResponseEntity.ok(accountService.getAccount(code));
  }

  @GetMapping("/{code}/statement")
  public ResponseEntity<StatementResponse> getStatement(
      @PathVariable String code,
      @RequestParam(required = false) Long cursor,
      @RequestParam(defaultValue = "50") int limit,
      Authentication authentication) {
    validateAccountAccess(code, authentication);
    return ResponseEntity.ok(accountService.getStatement(code, cursor, limit));
  }

  private void validateAccountAccess(String accountCode, Authentication authentication) {
    if (authentication == null) {
      return;
    }
    boolean isMerchant =
        authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_MERCHANT"));

    if (isMerchant) {
      String tokenSellerId = null;
      if (authentication.getPrincipal() instanceof Jwt jwt) {
        tokenSellerId = jwt.getClaimAsString("sellerId");
        if (tokenSellerId == null) {
          tokenSellerId = jwt.getClaimAsString("seller_id");
        }
      }

      // If merchant attempts to view non-seller account (e.g. system accounts or other sellers)
      if (!accountCode.startsWith("seller:") || tokenSellerId == null) {
        throw new PayCoreException(
            HttpStatus.FORBIDDEN,
            "forbidden",
            "Merchant may only access their own seller account data");
      }

      String[] parts = accountCode.split(":");
      if (parts.length < 2 || !parts[1].equalsIgnoreCase(tokenSellerId)) {
        throw new PayCoreException(
            HttpStatus.FORBIDDEN,
            "forbidden",
            "Merchant may only access their own seller account data");
      }
    }
  }
}
