package io.github.yesh.paycore.settlement.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class LedgerClientTest {

  private RestTemplate restTemplate;
  private LedgerClient ledgerClient;

  @BeforeEach
  void setUp() {
    restTemplate = mock(RestTemplate.class);
    ledgerClient =
        new LedgerClient(
            restTemplate,
            "http://localhost:8082",
            "http://localhost:8081",
            "settlement-service",
            "settlement_dev_secret");
  }

  @Test
  @DisplayName("Acquires M2M token from identity-service and caches it")
  void testTokenAcquisitionAndCaching() {
    Map<String, Object> tokenBody = Map.of("token", "jwt_token_123", "expiresInSeconds", 600);
    when(restTemplate.postForEntity(eq("http://localhost:8081/auth/service-token"), any(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(tokenBody, HttpStatus.OK));

    String token1 = ledgerClient.getServiceToken();
    assertThat(token1).isEqualTo("jwt_token_123");

    // Second call should return cached token without HTTP request
    String token2 = ledgerClient.getServiceToken();
    assertThat(token2).isEqualTo("jwt_token_123");

    verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8081/auth/service-token"), any(), eq(Map.class));
  }

  @Test
  @DisplayName("Proactively refreshes token when remaining validity is less than 60 seconds")
  void testProactiveTokenRefresh() {
    // Pre-set token expiring in 30 seconds
    ledgerClient.setCachedTokenForTesting("expiring_soon_jwt", Instant.now().plusSeconds(30));

    Map<String, Object> freshTokenBody = Map.of("token", "fresh_jwt_456", "expiresInSeconds", 600);
    when(restTemplate.postForEntity(eq("http://localhost:8081/auth/service-token"), any(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(freshTokenBody, HttpStatus.OK));

    String token = ledgerClient.getServiceToken();
    assertThat(token).isEqualTo("fresh_jwt_456");

    verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8081/auth/service-token"), any(), eq(Map.class));
  }

  @Test
  @DisplayName("Initiate payout sends Authorization and Idempotency-Key headers")
  void testInitiatePayoutHeaders() {
    ledgerClient.setCachedTokenForTesting("valid_token", Instant.now().plusSeconds(500));

    Map<String, Object> payoutResponse = Map.of("payoutId", "pay_batch_1", "status", "INITIATED");
    when(restTemplate.postForEntity(eq("http://localhost:8082/v1/payouts"), any(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(payoutResponse, HttpStatus.CREATED));

    Map<String, Object> result = ledgerClient.initiatePayout("pay_batch_1", "seller_99", 50000L);
    assertThat(result).isNotNull();
    assertThat(result.get("status")).isEqualTo("INITIATED");

    ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate).postForEntity(eq("http://localhost:8082/v1/payouts"), captor.capture(), eq(Map.class));

    HttpEntity entity = captor.getValue();
    HttpHeaders headers = entity.getHeaders();
    assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer valid_token");
    assertThat(headers.getFirst("Idempotency-Key")).isEqualTo("pay_batch_1");
  }

  @Test
  @DisplayName("Post provider deposit sends depositId with deposit: prefix in idempotency key")
  void testPostProviderDeposit() {
    ledgerClient.setCachedTokenForTesting("valid_token", Instant.now().plusSeconds(500));

    Map<String, Object> depositResponse = Map.of("depositId", "dep_55", "netAmount", 95000L);
    when(restTemplate.postForEntity(eq("http://localhost:8082/v1/provider-deposits"), any(), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(depositResponse, HttpStatus.CREATED));

    Map<String, Object> result =
        ledgerClient.postProviderDeposit("dep_55", 95000L, 5000L, "BANK_REF_55", "2026-11-16");
    assertThat(result).isNotNull();

    ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate).postForEntity(eq("http://localhost:8082/v1/provider-deposits"), captor.capture(), eq(Map.class));

    HttpEntity entity = captor.getValue();
    assertThat(entity.getHeaders().getFirst("Idempotency-Key")).isEqualTo("deposit:dep_55");
  }
}
