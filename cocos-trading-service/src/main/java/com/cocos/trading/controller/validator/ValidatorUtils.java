package com.cocos.trading.controller.validator;

import java.math.BigDecimal;
import org.springframework.validation.Errors;

/**
 * Reusable validation helper methods for Spring {@link org.springframework.validation.Validator} implementations.
 */
public final class ValidatorUtils {

    private ValidatorUtils() {
    }

    /**
     * Rejects a field if its value is {@code null}.
     *
     * @param errors         Spring {@link Errors} collector.
     * @param value          Value to inspect.
     * @param field          Field name.
     * @param errorCode      Validation error code.
     * @param defaultMessage Human-readable error message.
     */
    public static void rejectIfNull(
            Errors errors,
            Object value,
            String field,
            String errorCode,
            String defaultMessage
    ) {
        if (value == null) {
            errors.rejectValue(field, errorCode, defaultMessage);
        }
    }

    /**
     * Rejects a numeric {@link BigDecimal} field if it is less than or equal to zero.
     *
     * @param errors         Spring {@link Errors} collector.
     * @param value          Value to inspect.
     * @param field          Field name.
     * @param errorCode      Validation error code.
     * @param defaultMessage Human-readable error message.
     */
    public static void validatePositiveBigDecimal(
            Errors errors,
            BigDecimal value,
            String field,
            String errorCode,
            String defaultMessage
    ) {
        if (value != null && value.compareTo(BigDecimal.ZERO) <= 0) {
            errors.rejectValue(field, errorCode, defaultMessage);
        }
    }

    /**
     * Rejects an {@link Integer} field if it is less than or equal to zero.
     *
     * @param errors         Spring {@link Errors} collector.
     * @param value          Value to inspect.
     * @param field          Field name.
     * @param errorCode      Validation error code.
     * @param defaultMessage Human-readable error message.
     */
    public static void validatePositiveInteger(
            Errors errors,
            Integer value,
            String field,
            String errorCode,
            String defaultMessage
    ) {
        if (value != null && value <= 0) {
            errors.rejectValue(field, errorCode, defaultMessage);
        }
    }
}

