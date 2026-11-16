package io.github.yesh.paycore.ledger.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.ledger.domain.IdempotencyRecord;
import io.github.yesh.paycore.ledger.domain.IdempotencyStatus;
import io.github.yesh.paycore.ledger.repository.IdempotencyRecordRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

  @Mock private IdempotencyRecordRepository repository;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private IdempotencyService idempotencyService;

  @BeforeEach
  void setUp() {
    idempotencyService = new IdempotencyService(repository, objectMapper);
  }

  public record TestResult(String status, int code) {}

  @Test
  @DisplayName("First execution executes action, claims in-flight, and completes with cached JSON")
  void testFirstExecution() {
    String key = "idemp_first_exec";
    Map<String, Object> payload = Map.of("amount", 5000);

    when(repository.findByScopeAndKey(eq("global"), eq(key))).thenReturn(Optional.empty());

    IdempotencyRecord savedRecord =
        new IdempotencyRecord("global", key, "hash", IdempotencyStatus.IN_FLIGHT, null);
    when(repository.save(any(IdempotencyRecord.class))).thenReturn(savedRecord);

    AtomicInteger callCount = new AtomicInteger(0);
    TestResult result =
        idempotencyService.execute(
            key,
            payload,
            () -> {
              callCount.incrementAndGet();
              return new TestResult("SUCCESS", 200);
            },
            TestResult.class);

    assertThat(callCount.get()).isEqualTo(1);
    assertThat(result.status()).isEqualTo("SUCCESS");
    assertThat(result.code()).isEqualTo(200);

    verify(repository, atLeastOnce()).save(any(IdempotencyRecord.class));
  }

  @Test
  @DisplayName("Subsequent execution with same key and payload returns cached response without running action")
  void testSubsequentExecutionReturnsCache() throws Exception {
    String key = "idemp_cached";
    Map<String, Object> payload = Map.of("amount", 5000);
    String payloadJson = objectMapper.writeValueAsString(payload);
    String payloadHash = idempotencyService.hashPayload(payloadJson);

    TestResult cachedResult = new TestResult("SUCCESS", 200);
    String cachedJson = objectMapper.writeValueAsString(cachedResult);

    IdempotencyRecord completed =
        new IdempotencyRecord("global", key, payloadHash, IdempotencyStatus.COMPLETED, cachedJson);
    when(repository.findByScopeAndKey("global", key)).thenReturn(Optional.of(completed));

    AtomicInteger callCount = new AtomicInteger(0);
    TestResult result =
        idempotencyService.execute(
            key,
            payload,
            () -> {
              callCount.incrementAndGet();
              return new TestResult("NEW", 201);
            },
            TestResult.class);

    assertThat(callCount.get()).isEqualTo(0); // Action was NOT called!
    assertThat(result.status()).isEqualTo("SUCCESS");
    assertThat(result.code()).isEqualTo(200);
  }

  @Test
  @DisplayName("Reusing key with different payload throws 422 idempotency_key_reused")
  void testReusedKeyDifferentPayloadThrows() throws Exception {
    String key = "idemp_reuse";
    Map<String, Object> oldPayload = Map.of("amount", 5000);
    String oldHash = idempotencyService.hashPayload(objectMapper.writeValueAsString(oldPayload));

    IdempotencyRecord existing =
        new IdempotencyRecord("global", key, oldHash, IdempotencyStatus.COMPLETED, "{}");
    when(repository.findByScopeAndKey("global", key)).thenReturn(Optional.of(existing));

    Map<String, Object> newPayload = Map.of("amount", 9999); // different amount!

    assertThatThrownBy(
            () ->
                idempotencyService.execute(
                    key, newPayload, () -> new TestResult("FAIL", 500), TestResult.class))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY)
        .hasFieldOrPropertyWithValue("code", "idempotency_key_reused");
  }

  @Test
  @DisplayName("Concurrent request with status IN_FLIGHT throws 409 request_in_progress")
  void testConcurrentInFlightThrowsConflict() throws Exception {
    String key = "idemp_in_flight";
    Map<String, Object> payload = Map.of("amount", 5000);
    String payloadHash = idempotencyService.hashPayload(objectMapper.writeValueAsString(payload));

    IdempotencyRecord inFlight =
        new IdempotencyRecord("global", key, payloadHash, IdempotencyStatus.IN_FLIGHT, null);
    when(repository.findByScopeAndKey("global", key)).thenReturn(Optional.of(inFlight));

    assertThatThrownBy(
            () ->
                idempotencyService.execute(
                    key, payload, () -> new TestResult("FAIL", 500), TestResult.class))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT)
        .hasFieldOrPropertyWithValue("code", "request_in_progress");
  }

  @Test
  @DisplayName("Stale in-flight key (> 60s) is safely taken over after worker crash")
  void testStaleInFlightTakeover() throws Exception {
    String key = "idemp_stale";
    Map<String, Object> payload = Map.of("amount", 5000);
    String payloadHash = idempotencyService.hashPayload(objectMapper.writeValueAsString(payload));

    IdempotencyRecord staleRecord =
        new IdempotencyRecord("global", key, payloadHash, IdempotencyStatus.IN_FLIGHT, null);
    staleRecord.setCreatedAt(Instant.now().minus(75, ChronoUnit.SECONDS)); // 75s old!

    when(repository.findByScopeAndKey("global", key)).thenReturn(Optional.of(staleRecord));

    AtomicInteger callCount = new AtomicInteger(0);
    TestResult result =
        idempotencyService.execute(
            key,
            payload,
            () -> {
              callCount.incrementAndGet();
              return new TestResult("RECOVERED", 200);
            },
            TestResult.class);

    assertThat(callCount.get()).isEqualTo(1);
    assertThat(result.status()).isEqualTo("RECOVERED");
    assertThat(staleRecord.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
  }

  @Test
  @DisplayName("Caller scopes isolate same idempotency keys across different callers")
  void testScopedKeysDoNotCollide() {
    String key = "common_key_1";
    Map<String, Object> payload = Map.of("amount", 1000);

    when(repository.findByScopeAndKey("merchant_alice", key)).thenReturn(Optional.empty());
    when(repository.findByScopeAndKey("merchant_bob", key)).thenReturn(Optional.empty());

    TestResult r1 =
        idempotencyService.execute(
            "merchant_alice", key, payload, () -> new TestResult("ALICE", 200), TestResult.class);
    TestResult r2 =
        idempotencyService.execute(
            "merchant_bob", key, payload, () -> new TestResult("BOB", 200), TestResult.class);

    assertThat(r1.status()).isEqualTo("ALICE");
    assertThat(r2.status()).isEqualTo("BOB");
    verify(repository).save(argThat(rec -> "merchant_alice".equals(rec.getScope()) && key.equals(rec.getKey())));
    verify(repository).save(argThat(rec -> "merchant_bob".equals(rec.getScope()) && key.equals(rec.getKey())));
  }

  @Test
  @DisplayName("Previously failed or interrupted record can be retried successfully with same payload")
  void testFailedRecordCanBeRetriedSuccessfully() throws Exception {
    String key = "idemp_failed_retry";
    Map<String, Object> payload = Map.of("amount", 5000);
    String payloadHash = idempotencyService.hashPayload(objectMapper.writeValueAsString(payload));

    IdempotencyRecord failedRecord =
        new IdempotencyRecord("global", key, payloadHash, IdempotencyStatus.FAILED, null);

    when(repository.findByScopeAndKey("global", key)).thenReturn(Optional.of(failedRecord));

    AtomicInteger callCount = new AtomicInteger(0);
    TestResult result =
        idempotencyService.execute(
            key,
            payload,
            () -> {
              callCount.incrementAndGet();
              return new TestResult("RETRY_SUCCESS", 200);
            },
            TestResult.class);

    assertThat(callCount.get()).isEqualTo(1);
    assertThat(result.status()).isEqualTo("RETRY_SUCCESS");
    assertThat(failedRecord.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
  }

  @Test
  @DisplayName("Startup crash recovery resets orphaned IN_FLIGHT keys to FAILED")
  void testStartupRecoveryResetsInFlightToFailed() {
    when(repository.updateStatusByStatus(IdempotencyStatus.IN_FLIGHT, IdempotencyStatus.FAILED)).thenReturn(5);

    int recovered = idempotencyService.recoverOrphanedInFlightKeysOnStartup();

    assertThat(recovered).isEqualTo(5);
    verify(repository).updateStatusByStatus(IdempotencyStatus.IN_FLIGHT, IdempotencyStatus.FAILED);
  }

  @Test
  @DisplayName("7-day cleanup job purges expired completed/failed records")
  void testCleanupExpiredRecords() {
    when(repository.deleteByStatusInAndCreatedAtBefore(any(), any())).thenReturn(42);

    int purged = idempotencyService.cleanExpiredRecords();

    assertThat(purged).isEqualTo(42);
    verify(repository).deleteByStatusInAndCreatedAtBefore(any(), any());
  }
}
