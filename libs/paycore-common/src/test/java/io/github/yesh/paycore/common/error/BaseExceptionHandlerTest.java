package io.github.yesh.paycore.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public class BaseExceptionHandlerTest {

  private final BaseExceptionHandler handler = new BaseExceptionHandler();

  @Test
  @DisplayName("PayCoreException maps to appropriate HTTP status and RFC 9457 body")
  void testHandlePayCoreException() {
    PayCoreException ex =
        new PayCoreException(HttpStatus.CONFLICT, "idempotency_conflict", "Request in flight");

    ResponseEntity<ProblemDetails> response = handler.handlePayCoreException(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo(409);
    assertThat(response.getBody().code()).isEqualTo("idempotency_conflict");
    assertThat(response.getBody().detail()).isEqualTo("Request in flight");
  }

  @Test
  @DisplayName("Generic Exception maps to HTTP 500 internal server error")
  void testHandleGenericException() {
    Exception ex = new RuntimeException("Database timeout");

    ResponseEntity<ProblemDetails> response = handler.handleGenericException(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo(500);
    assertThat(response.getBody().code()).isEqualTo("internal_server_error");
  }
}
