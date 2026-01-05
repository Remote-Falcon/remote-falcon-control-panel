package com.remotefalcon.controlpanel.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class SequencesCsvExceptionHandler {

  @ExceptionHandler(SequencesCsvException.class)
  public ResponseEntity<Map<String, String>> handleSequencesCsvException(SequencesCsvException ex) {
    Map<String, String> body = Map.of(
        "errorType", ex.getErrorType().name(),
        "message", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }
}
