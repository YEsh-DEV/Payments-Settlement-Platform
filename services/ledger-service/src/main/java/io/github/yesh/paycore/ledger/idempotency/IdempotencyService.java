package io.github.yesh.paycore.ledger.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.ledger.domain.IdempotencyRecord;
import io.github.yesh.paycore.ledger.domain.IdempotencyStatus;
import io.github.yesh.paycore.ledger.repository.IdempotencyRecordRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

  private final IdempotencyRecordRepository repository;
  private final ObjectMapper objectMapper;

  public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public <T> T execute(
      String key, Object requestPayload, Supplier<T> action, Class<T> responseClass) {
    if (key == null || key.isBlank()) {
      return action.get();
    }

    String payloadJson = serialize(requestPayload);
    String requestHash = hashPayload(payloadJson);

    // Check existing record
    Optional<IdempotencyRecord> existing = repository.findById(key);
    if (existing.isPresent()) {
      IdempotencyRecord record = existing.get();
      if (!record.getRequestHash().equals(requestHash)) {
        throw new PayCoreException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "idempotency_conflict",
            "Idempotency key reused with different request payload");
      }
      if (record.getStatus() == IdempotencyStatus.IN_FLIGHT) {
        throw new PayCoreException(
            HttpStatus.CONFLICT,
            "concurrent_request",
            "A request with this idempotency key is currently processing");
      }
      if (record.getStatus() == IdempotencyStatus.COMPLETED && record.getResponseBody() != null) {
        return deserialize(record.getResponseBody(), responseClass);
      }
    }

    // Claim key as IN_FLIGHT
    claimKey(key, requestHash);

    try {
      T result = action.get();
      String responseJson = serialize(result);
      completeKey(key, responseJson);
      return result;
    } catch (Exception ex) {
      failKey(key);
      throw ex;
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void claimKey(String key, String requestHash) {
    IdempotencyRecord record =
        new IdempotencyRecord(key, requestHash, IdempotencyStatus.IN_FLIGHT, null);
    repository.save(record);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void completeKey(String key, String responseJson) {
    repository
        .findById(key)
        .ifPresent(
            record -> {
              record.setStatus(IdempotencyStatus.COMPLETED);
              record.setResponseBody(responseJson);
              repository.save(record);
            });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failKey(String key) {
    repository
        .findById(key)
        .ifPresent(
            record -> {
              record.setStatus(IdempotencyStatus.FAILED);
              repository.save(record);
            });
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

  private String serialize(Object obj) {
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
