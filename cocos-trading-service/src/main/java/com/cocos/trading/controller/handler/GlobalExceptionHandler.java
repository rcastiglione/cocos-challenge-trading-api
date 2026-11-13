package com.cocos.trading.controller.handler;

import com.cocos.trading.controller.problem.BadRequestProblem;
import com.cocos.trading.controller.problem.BusinessRuleViolationProblem;
import com.cocos.trading.controller.problem.ResourceNotFoundProblem;
import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import com.cocos.trading.domain.exception.InvalidOrderStateException;
import com.cocos.trading.domain.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.spring.web.advice.ProblemHandling;

/**
 * Global exception handler translating validation, JSON parsing, and domain exceptions
 * into standardized RFC 7807 {@link Problem} HTTP responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler implements ProblemHandling {

    @Override
    public ResponseEntity<Problem> handleMessageNotReadableException(
            HttpMessageNotReadableException exception,
            NativeWebRequest request
    ) {
        Throwable cause = exception.getCause();

        if (cause instanceof InvalidFormatException invalidFormat && !invalidFormat.getPath().isEmpty()) {
            String fieldName = invalidFormat.getPath().getFirst().getFieldName();
            String message = String.format("Invalid value '%s' for field '%s'.", invalidFormat.getValue(), fieldName);
            log.warn("Invalid format in JSON request: {}", message);
            return ResponseEntity.badRequest()
                    .body(new BadRequestProblem(message, Map.of("field", fieldName)));
        }

        if (cause instanceof MismatchedInputException mismatched && !mismatched.getPath().isEmpty()) {
            String fieldName = mismatched.getPath().getFirst().getFieldName();
            String message = String.format("Field '%s' is invalid or cannot be null.", fieldName);
            log.warn("Mismatched input in JSON request: {}", message);
            return ResponseEntity.badRequest()
                    .body(new BadRequestProblem(message, Map.of("field", fieldName)));
        }

        log.warn("Malformed JSON request", exception);
        return ResponseEntity.badRequest()
                .body(new BadRequestProblem("Malformed JSON request. Please check the request body format."));
    }

    @Override
    public ResponseEntity<Problem> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            NativeWebRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(
                    fieldError.getField(),
                    fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value"
            );
        }

        String summaryMessage = "Validation failed: " + String.join("; ", errors.values());
        log.warn("Request validation failed: {}", summaryMessage);
        return ResponseEntity.badRequest()
                .body(new BadRequestProblem(summaryMessage, errors));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Problem> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ResourceNotFoundProblem(ex.getResourceName(), ex.getIdentifier(), ex.getMessage()));
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<Problem> handleInvalidOrderStateException(InvalidOrderStateException ex) {
        log.warn("Invalid order state transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new BusinessRuleViolationProblem(
                        "Invalid Order State",
                        Status.CONFLICT,
                        ex.getMessage(),
                        Map.of(
                                "orderId", ex.getOrderId(),
                                "currentStatus", ex.getCurrentStatus().name()
                        )
                ));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<Problem> handleBusinessRuleViolationException(BusinessRuleViolationException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new BadRequestProblem(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("An unexpected error occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "An internal server error occurred. Please try again later."));
    }
}

