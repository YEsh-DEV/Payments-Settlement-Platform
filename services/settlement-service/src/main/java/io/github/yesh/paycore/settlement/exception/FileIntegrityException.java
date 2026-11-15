package io.github.yesh.paycore.settlement.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class FileIntegrityException extends RuntimeException {
  public FileIntegrityException(String message) {
    super(message);
  }
}
