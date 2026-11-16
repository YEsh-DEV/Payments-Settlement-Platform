package io.github.yesh.paycore.settlement.exception;

import io.github.yesh.paycore.common.error.PayCoreException;
import org.springframework.http.HttpStatus;

public class InvalidBatchStateException extends PayCoreException {
  public InvalidBatchStateException(String message) {
    super(HttpStatus.CONFLICT, "invalid_state_transition", message);
  }
}
