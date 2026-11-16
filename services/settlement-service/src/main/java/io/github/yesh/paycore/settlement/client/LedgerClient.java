package io.github.yesh.paycore.settlement.client;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class LedgerClient {

  private static final Logger log = LoggerFactory.getLogger(LedgerClient.class);

  private final RestTemplate restTemplate;
  private final String ledgerUrl;
  private final String identityUrl;
  private final String clientId;
  private final String clientSecret;

  private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>();

  public record CachedToken(String token, Instant expiresAt) {
    public boolean isValid() {
      // Proactively refresh when < 60 seconds remain before expiration
      return token != null && Instant.now().plusSeconds(60).isBefore(expiresAt);
    }
  }

  @org.springframework.beans.factory.annotation.Autowired
  public LedgerClient(
      RestTemplateBuilder restTemplateBuilder,
      @Value("${paycore.settlement.ledger-url:http://localhost:8082}") String ledgerUrl,
      @Value("${paycore.settlement.identity-url:http://localhost:8081}") String identityUrl,
      @Value("${paycore.settlement.client-id:settlement-service}") String clientId,
      @Value("${paycore.settlement.client-secret:settlement_dev_secret}") String clientSecret) {
    this.restTemplate = restTemplateBuilder.build();
    this.ledgerUrl =
        ledgerUrl.endsWith("/") ? ledgerUrl.substring(0, ledgerUrl.length() - 1) : ledgerUrl;
    this.identityUrl =
        identityUrl.endsWith("/")
            ? identityUrl.substring(0, identityUrl.length() - 1)
            : identityUrl;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  // Constructor for testing with mock RestTemplate
  public LedgerClient(
      RestTemplate restTemplate,
      String ledgerUrl,
      String identityUrl,
      String clientId,
      String clientSecret) {
    this.restTemplate = restTemplate;
    this.ledgerUrl =
        ledgerUrl.endsWith("/") ? ledgerUrl.substring(0, ledgerUrl.length() - 1) : ledgerUrl;
    this.identityUrl =
        identityUrl.endsWith("/")
            ? identityUrl.substring(0, identityUrl.length() - 1)
            : identityUrl;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  public synchronized String getServiceToken() {
    CachedToken current = tokenCache.get();
    if (current != null && current.isValid()) {
      return current.token();
    }

    String url = identityUrl + "/auth/service-token";
    Map<String, String> body = Map.of("clientId", clientId, "clientSecret", clientSecret);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

    try {
      ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
      Map<?, ?> respBody = response.getBody();
      if (response.getStatusCode().is2xxSuccessful() && respBody != null) {
        String token = (String) respBody.get("accessToken");
        if (token == null) {
          token = (String) respBody.get("token");
        }
        Number expiresIn = (Number) respBody.get("expiresIn");
        if (expiresIn == null) {
          expiresIn = (Number) respBody.get("expiresInSeconds");
        }
        if (token != null) {
          long duration = expiresIn != null ? expiresIn.longValue() : 600L;
          Instant expiresAt = Instant.now().plusSeconds(duration);
          tokenCache.set(new CachedToken(token, expiresAt));
          log.info("Acquired fresh service token from identity-service, valid for {}s", duration);
          return token;
        }
      }
      throw new RuntimeException(
          "Failed to acquire service token: status " + response.getStatusCode());
    } catch (Exception e) {
      log.error("Error calling identity-service for service token: {}", e.getMessage());
      throw new RuntimeException("Service token acquisition failed", e);
    }
  }

  public void setCachedTokenForTesting(String token, Instant expiresAt) {
    this.tokenCache.set(new CachedToken(token, expiresAt));
  }

  public CachedToken getCachedToken() {
    return this.tokenCache.get();
  }

  private HttpHeaders createAuthHeaders(String idempotencyKey) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(getServiceToken());
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      headers.set("Idempotency-Key", idempotencyKey);
    }
    return headers;
  }

  public Map<String, Object> initiatePayout(String payoutId, String sellerId, long amountPaise) {
    String url = ledgerUrl + "/v1/payouts";
    Map<String, Object> body =
        Map.of("payoutId", payoutId, "sellerId", sellerId, "amountPaise", amountPaise);
    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, createAuthHeaders(payoutId));

    try {
      ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
      return response.getBody();
    } catch (HttpStatusCodeException ex) {
      log.error(
          "Ledger initiatePayout failed with status {}: {}",
          ex.getStatusCode(),
          ex.getResponseBodyAsString());
      throw ex;
    }
  }

  public Map<String, Object> confirmPayout(String payoutId, String batchId) {
    String url =
        ledgerUrl
            + "/v1/payouts/"
            + payoutId
            + "/confirm?batchId="
            + (batchId != null ? batchId : "default");
    HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders(null));

    try {
      ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
      return response.getBody();
    } catch (HttpStatusCodeException ex) {
      log.error(
          "Ledger confirmPayout failed with status {}: {}",
          ex.getStatusCode(),
          ex.getResponseBodyAsString());
      throw ex;
    }
  }

  public Map<String, Object> failPayout(String payoutId, String reason) {
    String url =
        ledgerUrl
            + "/v1/payouts/"
            + payoutId
            + "/fail?reason="
            + (reason != null ? reason : "Bank failure");
    HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders(null));

    try {
      ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
      return response.getBody();
    } catch (HttpStatusCodeException ex) {
      log.error(
          "Ledger failPayout failed with status {}: {}",
          ex.getStatusCode(),
          ex.getResponseBodyAsString());
      throw ex;
    }
  }

  public Map<String, Object> postProviderDeposit(
      String depositId, long netAmount, long feeAmount, String reference, String valueDate) {
    String url = ledgerUrl + "/v1/provider-deposits";
    Map<String, Object> body = new HashMap<>();
    body.put("depositId", depositId);
    body.put("netAmount", netAmount);
    body.put("feeAmount", feeAmount);
    body.put("reference", reference);
    body.put("valueDate", valueDate);

    HttpEntity<Map<String, Object>> entity =
        new HttpEntity<>(body, createAuthHeaders("deposit:" + depositId));

    try {
      ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
      return response.getBody();
    } catch (HttpStatusCodeException ex) {
      log.error(
          "Ledger postProviderDeposit failed with status {}: {}",
          ex.getStatusCode(),
          ex.getResponseBodyAsString());
      throw ex;
    }
  }

  public Map<String, Object> postAdjustment(
      String adjustmentId,
      String debitAccount,
      String creditAccount,
      long amountPaise,
      String caseId,
      String reason) {
    String url = ledgerUrl + "/v1/adjustments";
    Map<String, Object> body = new HashMap<>();
    body.put("adjustmentId", adjustmentId);
    body.put("debitAccount", debitAccount);
    body.put("creditAccount", creditAccount);
    body.put("amountPaise", amountPaise);
    body.put("caseId", caseId);
    body.put("reason", reason);

    HttpEntity<Map<String, Object>> entity =
        new HttpEntity<>(body, createAuthHeaders("adjustment:" + adjustmentId));

    try {
      ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
      return response.getBody();
    } catch (HttpStatusCodeException ex) {
      log.error(
          "Ledger postAdjustment failed with status {}: {}",
          ex.getStatusCode(),
          ex.getResponseBodyAsString());
      throw ex;
    }
  }
}
