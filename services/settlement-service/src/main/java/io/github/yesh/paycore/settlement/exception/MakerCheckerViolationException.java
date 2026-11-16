package io.github.yesh.paycore.settlement.exception;

import io.github.yesh.paycore.common.error.PayCoreException;
import org.springframework.http.HttpStatus;

public class MakerCheckerViolationException extends PayCoreException {
  public MakerCheckerViolationException(String message) {
    super(HttpStatus.UNPROCESSABLE_ENTITY, "maker_checker_violation", message);
  }
}
