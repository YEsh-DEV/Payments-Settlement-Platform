package io.github.yesh.paycore.ledger.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.ledger.domain.IdempotencyRecord;
import io.github.yesh.paycore.ledger.domain.IdempotencyStatus;
import io.github.yesh.paycore.ledger.repository.IdempotencyRecordRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Robust Idempotency Service adhering to Playbook Section 8.2: - Scoped by caller identity from
 * authentication token. - HTTP 409 request_in_progress when key is in-flight. - HTTP 422
 * idempotency_key_reused when payload hash mismatches. - Stale in-flight takeover after 60s
 * timeout. - Response header Idempotent-Replayed: true upon cache replay. - 7-day TTL cleanup
 * scheduled daemon.
 */
@Service
public class IdempotencyService {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
  public static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";
  public static final long STALE_IN_FLIGHT_TIMEOUT_SECONDS = 60L;

  private final IdempotencyRecordRepository repository;
  private final ObjectMapper objectMapper;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  @org.springframework.context.annotation.Lazy
  private IdempotencyService self;

  public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  private IdempotencyService getSelf() {
    return self != null ? self : this;
  }

  public <T> T execute(
      String key, Object requestPayload, Supplier<T> action, Class<T> responseClass) {
    return execute(resolveCurrentScope(), key, requestPayload, action, responseClass);
  }

  public <T> T execute(
      String scope, String key, Object requestPayload, Supplier<T> action, Class<T> responseClass) {
    if (key == null || key.isBlank()) {
      return action.get();
    }

    String resolvedScope = (scope != null && !scope.isBlank()) ? scope : "global";
    String payloadJson = serialize(requestPayload);
    String requestHash = hashPayload(payloadJson);

    // 1. Check existing record
    Optional<IdempotencyRecord> existing = repository.findByScopeAndKey(resolvedScope, key);
    if (existing.isPresent()) {
      IdempotencyRecord record = existing.get();

      // Invariant: payload hash verification
      if (!record.getRequestHash().equals(requestHash)) {
        throw new PayCoreException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "idempotency_key_reused",
            "Idempotency key reused with different request payload");
      }

      if (record.getStatus() == IdempotencyStatus.IN_FLIGHT) {
        // Stale in-flight recovery after 60 seconds
        if (record
            .getCreatedAt()
            .isBefore(Instant.now().minusSeconds(STALE_IN_FLIGHT_TIMEOUT_SECONDS))) {
          log.warn(
              "Taking over stale in-flight idempotency key {} (scoped: {}) created at {}",
              key,
              resolvedScope,
              record.getCreatedAt());
          getSelf().takeOverStaleKey(resolvedScope, key, requestHash);
        } else {
          throw new PayCoreException(
              HttpStatus.CONFLICT,
              "request_in_progress",
              "A request with this idempotency key is currently processing");
        }
      } else if (record.getStatus() == IdempotencyStatus.FAILED) {
        log.info(
            "Retrying previously interrupted or failed idempotency key {} (scoped: {})",
            key,
            resolvedScope);
        getSelf().takeOverStaleKey(resolvedScope, key, requestHash);
      } else if (record.getStatus() == IdempotencyStatus.COMPLETED
          && record.getResponseBody() != null) {
        markReplayedHeader(record.getResponseStatusCode());
        return deserialize(record.getResponseBody(), responseClass);
      }
    } else {
      // 2. Claim key as IN_FLIGHT
      try {
        getSelf().claimKey(resolvedScope, key, requestHash);
      } catch (DataIntegrityViolationException ex) {
        // Concurrent race condition handling
        Optional<IdempotencyRecord> concurrent = repository.findByScopeAndKey(resolvedScope, key);
        if (concurrent.isPresent()) {
          IdempotencyRecord rec = concurrent.get();
          if (!rec.getRequestHash().equals(requestHash)) {
            throw new PayCoreException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "idempotency_key_reused",
                "Idempotency key reused with different request payload");
          }
          if (rec.getStatus() == IdempotencyStatus.IN_FLIGHT) {
            throw new PayCoreException(
                HttpStatus.CONFLICT,
                "request_in_progress",
                "A request with this idempotency key is currently processing");
          }
          if (rec.getStatus() == IdempotencyStatus.COMPLETED && rec.getResponseBody() != null) {
            markReplayedHeader(rec.getResponseStatusCode());
            return deserialize(rec.getResponseBody(), responseClass);
          }
        }
        throw ex;
      }
    }

    // 3. Execute downstream business logic
    try {
      T result = action.get();
      Optional<IdempotencyRecord> current = repository.findByScopeAndKey(resolvedScope, key);
      if (current.isPresent() && current.get().getStatus() == IdempotencyStatus.IN_FLIGHT) {
        String responseJson = serialize(result);
        getSelf().completeKey(resolvedScope, key, responseJson, 201);
      }
      return result;
    } catch (Exception ex) {
      getSelf().failKey(resolvedScope, key);
      throw ex;
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void claimKey(String scope, String key, String requestHash) {
    IdempotencyRecord record =
        new IdempotencyRecord(scope, key, requestHash, IdempotencyStatus.IN_FLIGHT, null);
    repository.save(record);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void takeOverStaleKey(String scope, String key, String requestHash) {
    repository
        .findByScopeAndKey(scope, key)
        .ifPresent(
            record -> {
              record.setStatus(IdempotencyStatus.IN_FLIGHT);
              record.setCreatedAt(Instant.now());
              record.setResponseBody(null);
              repository.save(record);
            });
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public void completeKey(String scope, String key, String responseJson, int statusCode) {
    repository
        .findByScopeAndKey(scope, key)
        .ifPresent(
            record -> {
              record.setStatus(IdempotencyStatus.COMPLETED);
              record.setResponseBody(responseJson);
              record.setResponseStatusCode(statusCode);
              repository.save(record);
            });
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public void completeKey(String scope, String key, String responseJson) {
    completeKey(scope, key, responseJson, 201);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failKey(String scope, String key) {
    repository
        .findByScopeAndKey(scope, key)
        .ifPresent(
            record -> {
              record.setStatus(IdempotencyStatus.FAILED);
              repository.save(record);
            });
  }

  @Transactional
  public int recoverOrphanedInFlightKeysOnStartup() {
    Instant cutoff = Instant.now().minusSeconds(STALE_IN_FLIGHT_TIMEOUT_SECONDS);
    int updated =
        repository.updateStatusByStatusAndCreatedAtBefore(
            IdempotencyStatus.IN_FLIGHT, IdempotencyStatus.FAILED, cutoff);
    if (updated > 0) {
      log.info("Reset {} stale IN_FLIGHT idempotency keys (>60s) to FAILED", updated);
    }
    return updated;
  }

  @Scheduled(cron = "${paycore.ledger.idempotency.cleanup-cron:0 0 3 * * *}")
  @Transactional
  public int cleanExpiredRecords() {
    Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
    int deleted =
        repository.deleteByStatusInAndCreatedAtBefore(
            List.of(IdempotencyStatus.COMPLETED, IdempotencyStatus.FAILED), cutoff);
    log.info("Idempotency cleanup sweep completed. Purged {} records older than 7 days.", deleted);
    return deleted;
  }

  public String resolveCurrentScope() {
    try {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null
          && auth.isAuthenticated()
          && auth.getName() != null
          && !auth.getName().isBlank()) {
        return auth.getName();
      }
    } catch (Exception ignored) {
    }
    return "global";
  }

  private void markReplayedHeader(Integer statusCode) {
    try {
      ServletRequestAttributes attrs =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs != null && attrs.getResponse() != null) {
        HttpServletResponse response = attrs.getResponse();
        response.setHeader(IDEMPOTENT_REPLAYED_HEADER, "true");
        if (statusCode != null) {
          response.setStatus(statusCode);
        }
      }
    } catch (Exception ignored) {
    }
  }

  private void markReplayedHeader() {
    markReplayedHeader(200);
  }

  public String hashPayload(String payload) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        String h = Integer.toHexString(0xff & b);
        if (h.length() == 1) hex.append('0');
        hex.append(h);
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  public String serialize(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize object", e);
    }
  }

  private <T> T deserialize(String json, Class<T> clazz) {
    try {
      return objectMapper.readValue(json, clazz);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialize idempotency response", e);
    }
  }
}
