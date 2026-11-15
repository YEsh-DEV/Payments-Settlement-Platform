package io.github.yesh.paycore.settlement.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidBatchStateException extends RuntimeException {
  public InvalidBatchStateException(String message) {
    super(message);
  }
}
