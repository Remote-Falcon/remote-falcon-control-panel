package com.remotefalcon.controlpanel.exception;

public class SequencesCsvException extends RuntimeException {
  public enum ErrorType {
    FILE_REQUIRED,
    SHOW_NOT_FOUND,
    EMPTY_FILE,
    INVALID_HEADERS,
    INVALID_COLUMN_COUNT,
    MISSING_SEQUENCE_NAME,
    READ_FAILURE
  }

  private final ErrorType errorType;

  public SequencesCsvException(ErrorType errorType, String message) {
    super(message);
    this.errorType = errorType;
  }

  public SequencesCsvException(ErrorType errorType, String message, Throwable cause) {
    super(message, cause);
    this.errorType = errorType;
  }

  public ErrorType getErrorType() {
    return errorType;
  }
}
