package io.github.yesh.paycore.common.error;

import io.github.yesh.paycore.common.web.RequestIdFilter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Global centralized exception handler producing RFC 9457 Problem Details. */
@RestControllerAdvice
public class BaseExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(BaseExceptionHandler.class);

  @ExceptionHandler(PayCoreException.class)
  public ResponseEntity<ProblemDetails> handlePayCoreException(PayCoreException ex) {
    String requestId = RequestIdFilter.getCurrentRequestId();
    log.warn(
        "Domain exception [requestId={}, code={}, status={}]: {}",
        requestId,
        ex.getCode(),
        ex.getStatus(),
        ex.getMessage());

    ProblemDetails details =
        ProblemDetails.builder()
            .type("https://api.paycore.dev/errors/" + ex.getCode().replace('_', '-'))
            .title(ex.getStatus().getReasonPhrase())
            .status(ex.getStatus().value())
            .code(ex.getCode())
            .detail(ex.getMessage())
            .requestId(requestId)
            .timestamp(Instant.now())
            .build();

    return ResponseEntity.status(ex.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(details);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetails> handleValidationException(
      MethodArgumentNotValidException ex) {
    String requestId = RequestIdFilter.getCurrentRequestId();
    List<ProblemDetails.InvalidParam> invalidParams = new ArrayList<>();
    for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
      invalidParams.add(
          new ProblemDetails.InvalidParam(fieldError.getField(), fieldError.getDefaultMessage()));
    }

    ProblemDetails details =
        ProblemDetails.builder()
            .type("https://api.paycore.dev/errors/validation-failed")
            .title("Validation Failed")
            .status(HttpStatus.BAD_REQUEST.value())
            .code("validation_failed")
            .detail("One or more request fields failed schema validation")
            .requestId(requestId)
            .timestamp(Instant.now())
            .invalidParams(invalidParams)
            .build();

    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(details);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetails> handleGenericException(Exception ex) {
    String requestId = RequestIdFilter.getCurrentRequestId();
    log.error("Unhandled internal server error [requestId={}]: ", requestId, ex);

    ProblemDetails details =
        ProblemDetails.builder()
            .type("https://api.paycore.dev/errors/internal-server-error")
            .title("Internal Server Error")
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .code("internal_server_error")
            .detail("An unexpected error occurred. Reference correlation ID: " + requestId)
            .requestId(requestId)
            .timestamp(Instant.now())
            .build();

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(details);
  }
}
