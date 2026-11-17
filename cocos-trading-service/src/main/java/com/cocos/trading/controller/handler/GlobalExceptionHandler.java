package com.cocos.trading.controller.handler;

import com.cocos.trading.api.request.TransferOrderRequestApi;
import com.cocos.trading.controller.problem.BadRequestProblem;
import com.cocos.trading.controller.problem.BusinessRuleViolationProblem;
import com.cocos.trading.controller.problem.ResourceNotFoundProblem;
import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import com.cocos.trading.domain.exception.DuplicateOrderException;
import com.cocos.trading.domain.exception.IdempotencyMismatchException;
import com.cocos.trading.domain.exception.InvalidOrderStateException;
import com.cocos.trading.domain.exception.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Global exception handler translating validation, JSON parsing, and domain exceptions
 * into standardized RFC 7807 {@link ProblemDetail} HTTP responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMessageNotReadableException(HttpMessageNotReadableException exception) {
        Throwable cause = exception.getCause();

        if (cause instanceof UnrecognizedPropertyException unrecognized) {
            String fieldName = unrecognized.getPropertyName();
            String message;
            if (unrecognized.getReferringClass() != null
                    && TransferOrderRequestApi.class.isAssignableFrom(unrecognized.getReferringClass())) {
                message = String.format(
                        "Unrecognized field '%s'. Cash transfers (CASH_IN/CASH_OUT) only accept 'userId', 'side', and 'amount'.",
                        fieldName
                );
            } else {
                message = String.format("Unrecognized field '%s' is not allowed for this request type.", fieldName);
            }
            log.warn("Unrecognized property in JSON request: {}", message);
            return problemResponse(HttpStatus.BAD_REQUEST, new BadRequestProblem(message, Map.of("field", fieldName)));
        }

        if (cause instanceof InvalidFormatException invalidFormat && !invalidFormat.getPath().isEmpty()) {
            String fieldName = invalidFormat.getPath().getFirst().getPropertyName();
            String message = String.format("Invalid value '%s' for field '%s'.", invalidFormat.getValue(), fieldName);
            log.warn("Invalid format in JSON request: {}", message);
            return problemResponse(HttpStatus.BAD_REQUEST, new BadRequestProblem(message, Map.of("field", fieldName)));
        }

        if (cause instanceof MismatchedInputException mismatched && !mismatched.getPath().isEmpty()) {
            String fieldName = mismatched.getPath().getFirst().getPropertyName();
            String message = String.format("Field '%s' is invalid or cannot be null.", fieldName);
            log.warn("Mismatched input in JSON request: {}", message);
            return problemResponse(HttpStatus.BAD_REQUEST, new BadRequestProblem(message, Map.of("field", fieldName)));
        }

        log.warn("Malformed JSON request: {}", exception.getMessage());
        return problemResponse(
                HttpStatus.BAD_REQUEST,
                new BadRequestProblem("Malformed JSON request. Please check the request body format.")
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(
                    fieldError.getField(),
                    fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value"
            );
        }

        String summaryMessage = "Validation failed: " + String.join("; ", errors.values());
        log.warn("Request validation failed: {}", summaryMessage);
        return problemResponse(HttpStatus.BAD_REQUEST, new BadRequestProblem(summaryMessage, errors));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        String message = String.format("Required request header '%s' is missing.", ex.getHeaderName());
        log.warn("Missing request header: {}", message);
        return problemResponse(HttpStatus.BAD_REQUEST, new BadRequestProblem(message, Map.of("header", ex.getHeaderName())));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        String parameterName = ex.getName();
        String message = String.format("Invalid value '%s' for parameter '%s'.", ex.getValue(), parameterName);
        log.warn("Parameter type mismatch: {}", message);
        return problemResponse(HttpStatus.BAD_REQUEST, new BadRequestProblem(message, Map.of("parameter", parameterName)));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return problemResponse(
                HttpStatus.NOT_FOUND,
                new ResourceNotFoundProblem(ex.getResourceName(), ex.getIdentifier(), ex.getMessage())
        );
    }

    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateOrderException(DuplicateOrderException ex) {
        log.warn("Duplicate order request (idempotency conflict): {}", ex.getMessage());
        return problemResponse(
                HttpStatus.CONFLICT,
                new BusinessRuleViolationProblem(
                        "Duplicate Order",
                        HttpStatus.CONFLICT,
                        ex.getMessage(),
                        Map.of("idempotencyKey", ex.getIdempotencyKey().toString())
                )
        );
    }

    @ExceptionHandler(IdempotencyMismatchException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyMismatchException(IdempotencyMismatchException ex) {
        log.warn("Idempotency key payload mismatch for key={}: {}", ex.getIdempotencyKey(), ex.getMessage());
        return problemResponse(
                HttpStatus.BAD_REQUEST,
                new BadRequestProblem(
                        ex.getMessage(),
                        Map.of("idempotencyKey", ex.getIdempotencyKey().toString())
                )
        );
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ProblemDetail> handleInvalidOrderStateException(InvalidOrderStateException ex) {
        log.warn("Invalid order state transition: {}", ex.getMessage());
        return problemResponse(
                HttpStatus.CONFLICT,
                new BusinessRuleViolationProblem(
                        "Invalid Order State",
                        HttpStatus.CONFLICT,
                        ex.getMessage(),
                        Map.of(
                                "orderId", ex.getOrderId(),
                                "currentStatus", ex.getCurrentStatus().name()
                        )
                )
        );
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRuleViolationException(BusinessRuleViolationException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return problemResponse(
                HttpStatus.CONFLICT,
                new BusinessRuleViolationProblem(
                        "Business Rule Violation",
                        HttpStatus.CONFLICT,
                        ex.getMessage(),
                        null
                )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("An unexpected error occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "An internal server error occurred. Please try again later."));
    }

    private static ResponseEntity<ProblemDetail> problemResponse(HttpStatus status, ProblemDetail problemDetail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }
}
