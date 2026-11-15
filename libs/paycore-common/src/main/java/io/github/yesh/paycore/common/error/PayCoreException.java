package io.github.yesh.paycore.common.error;

import org.springframework.http.HttpStatus;

/** Base domain exception carrying an RFC 9457 stable error code and HTTP status. */
public class PayCoreException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public PayCoreException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public PayCoreException(HttpStatus status, String code, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
    this.code = code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }
}
