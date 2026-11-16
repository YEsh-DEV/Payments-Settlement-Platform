package io.github.yesh.paycore.settlement.exception;

import io.github.yesh.paycore.common.error.PayCoreException;
import org.springframework.http.HttpStatus;

public class InvalidTransitionException extends PayCoreException {
  public InvalidTransitionException(String message) {
    super(HttpStatus.CONFLICT, "invalid_state_transition", message);
  }
}
