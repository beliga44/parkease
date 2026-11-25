package com.parkease.exception;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
    }

    @ExceptionHandler(ReservationConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ReservationConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), List.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(IllegalStateException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        if (isMissingBody(ex)) {
            return build(HttpStatus.BAD_REQUEST, "Request body is required", List.of());
        }
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            return build(HttpStatus.BAD_REQUEST, "Validation failed", List.of(new ErrorResponse.FieldError(
                    fieldPath(mismatch), expectedFormat(mismatch.getTargetType()))));
        }
        return build(HttpStatus.BAD_REQUEST, "Malformed JSON request body", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Validation failed", List.of(new ErrorResponse.FieldError(
                ex.getName(), expectedFormat(ex.getRequiredType()))));
    }

    // Spring has no dedicated exception type for a missing @RequestBody; this message prefix is
    // what RequestResponseBodyMethodProcessor uses. ParkingApiControllerTest guards it.
    private static boolean isMissingBody(HttpMessageNotReadableException ex) {
        return ex.getMessage() != null && ex.getMessage().startsWith("Required request body is missing");
    }

    private static String fieldPath(MismatchedInputException ex) {
        return ex.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
    }

    // The rejected value is not echoed back, so user input never appears in error responses.
    private static String expectedFormat(Class<?> type) {
        if (type != null && type.isEnum()) {
            return "must be one of " + Arrays.toString(type.getEnumConstants());
        }
        if (type == LocalDateTime.class) {
            return "must be an ISO-8601 date-time, e.g. 2024-03-15T09:00:00";
        }
        return "has an invalid value";
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
            return build(status, springError.getBody().getDetail(), List.of());
        }

        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error occurred", List.of());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message,
                                                List<ErrorResponse.FieldError> fieldErrors) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                status.value(), status.getReasonPhrase(), message, fieldErrors, LocalDateTime.now(clock)));
    }
}
