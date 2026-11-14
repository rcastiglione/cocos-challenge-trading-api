package com.cocos.trading.controller.validator;

import com.cocos.trading.api.common.MonetaryAmount;
import java.math.BigDecimal;
import org.springframework.validation.Errors;

/**
 * Reusable validation helper methods for Spring {@link org.springframework.validation.Validator} implementations.
 */
public final class ValidatorUtils {

    private static final String SUPPORTED_CURRENCY = MonetaryAmount.DEFAULT_CURRENCY;

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
     * Validates a {@link MonetaryAmount} field when present: requires a positive {@code amount} ({@code > 0})
     * and an explicit {@code "ARS"} currency code.
     *
     * @param errors         Spring {@link Errors} collector.
     * @param value          MonetaryAmount to inspect.
     * @param field          Field name (e.g., {@code "amount"} or {@code "price"}).
     * @param errorCode      Validation error code.
     * @param defaultMessage Human-readable error message when amount is missing or not positive.
     */
    public static void validateMonetaryAmount(
            Errors errors,
            MonetaryAmount value,
            String field,
            String errorCode,
            String defaultMessage
    ) {
        if (value == null) {
            return;
        }
        if (value.amount() == null || value.amount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.rejectValue(field, errorCode, defaultMessage);
        }
        if (value.currency() == null || !SUPPORTED_CURRENCY.equalsIgnoreCase(value.currency().trim())) {
            errors.rejectValue(
                    field,
                    field + ".currency.invalid",
                    field + ".currency is required and must be 'ARS'."
            );
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
