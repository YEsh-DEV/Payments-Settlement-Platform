package io.github.yesh.paycore.ledger.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.ledger.domain.IdempotencyRecord;
import io.github.yesh.paycore.ledger.domain.IdempotencyStatus;
import io.github.yesh.paycore.ledger.repository.IdempotencyRecordRepository;
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
  @DisplayName(
      "First execution executes action, claims in-flight, and marks completed with cached JSON")
  void testFirstExecution() {
    String key = "idemp_first_exec";
    Map<String, Object> payload = Map.of("amount", 5000);
    String payloadHash = idempotencyService.hashPayload(idempotencyService.hashPayload("dummy"));

    when(repository.findById(key)).thenReturn(Optional.empty());

    IdempotencyRecord savedRecord =
        new IdempotencyRecord(key, "hash", IdempotencyStatus.IN_FLIGHT, null);
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
  @DisplayName(
      "Subsequent execution with same key and payload returns cached response without running action")
  void testSubsequentExecutionReturnsCache() throws Exception {
    String key = "idemp_cached";
    Map<String, Object> payload = Map.of("amount", 5000);
    String payloadJson = objectMapper.writeValueAsString(payload);
    String payloadHash = idempotencyService.hashPayload(payloadJson);

    TestResult cachedResult = new TestResult("SUCCESS", 200);
    String cachedJson = objectMapper.writeValueAsString(cachedResult);

    IdempotencyRecord completed =
        new IdempotencyRecord(key, payloadHash, IdempotencyStatus.COMPLETED, cachedJson);
    when(repository.findById(key)).thenReturn(Optional.of(completed));

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
  @DisplayName("Reusing idempotency key with different payload throws 422 Unprocessable Entity")
  void testReusedKeyDifferentPayloadThrows() throws Exception {
    String key = "idemp_reuse";
    Map<String, Object> oldPayload = Map.of("amount", 5000);
    String oldHash = idempotencyService.hashPayload(objectMapper.writeValueAsString(oldPayload));

    IdempotencyRecord existing =
        new IdempotencyRecord(key, oldHash, IdempotencyStatus.COMPLETED, "{}");
    when(repository.findById(key)).thenReturn(Optional.of(existing));

    Map<String, Object> newPayload = Map.of("amount", 9999); // different amount!

    assertThatThrownBy(
            () ->
                idempotencyService.execute(
                    key, newPayload, () -> new TestResult("FAIL", 500), TestResult.class))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  @DisplayName("Concurrent request with status IN_FLIGHT throws 409 Conflict")
  void testConcurrentInFlightThrowsConflict() throws Exception {
    String key = "idemp_in_flight";
    Map<String, Object> payload = Map.of("amount", 5000);
    String payloadHash = idempotencyService.hashPayload(objectMapper.writeValueAsString(payload));

    IdempotencyRecord inFlight =
        new IdempotencyRecord(key, payloadHash, IdempotencyStatus.IN_FLIGHT, null);
    when(repository.findById(key)).thenReturn(Optional.of(inFlight));

    assertThatThrownBy(
            () ->
                idempotencyService.execute(
                    key, payload, () -> new TestResult("FAIL", 500), TestResult.class))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
  }
}
