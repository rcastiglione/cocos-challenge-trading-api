package com.cocos.trading.controller.validator;

import com.cocos.trading.api.common.MonetaryAmount;
import java.math.BigDecimal;
import java.util.function.Function;
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
        validateMonetaryAmount(
                errors,
                value,
                field,
                errorCode,
                defaultMessage,
                currency -> String.format("Currency '%s' is not supported. Only 'ARS' is currently allowed.", currency)
        );
    }

    /**
     * Validates a {@link MonetaryAmount} field when present: requires a positive {@code amount} ({@code > 0})
     * and an explicit {@code "ARS"} currency code, allowing callers to customize the unsupported-currency message.
     *
     * @param errors                           Spring {@link Errors} collector.
     * @param value                            MonetaryAmount to inspect.
     * @param field                            Field name (e.g., {@code "amount"} or {@code "price"}).
     * @param errorCode                        Validation error code.
     * @param defaultMessage                   Human-readable error message when amount is missing or not positive.
     * @param unsupportedCurrencyMessageBuilder Function formatting the message for a non-blank unsupported currency.
     */
    public static void validateMonetaryAmount(
            Errors errors,
            MonetaryAmount value,
            String field,
            String errorCode,
            String defaultMessage,
            Function<String, String> unsupportedCurrencyMessageBuilder
    ) {
        if (value == null) {
            return;
        }
        if (value.amount() == null || value.amount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.rejectValue(field, errorCode, defaultMessage);
        }
        if (value.currency() == null || value.currency().isBlank()) {
            errors.rejectValue(
                    field,
                    field + ".currency.invalid",
                    field + ".currency is required and must be 'ARS'."
            );
        } else if (!SUPPORTED_CURRENCY.equalsIgnoreCase(value.currency().trim())) {
            errors.rejectValue(
                    field,
                    field + ".currency.invalid",
                    unsupportedCurrencyMessageBuilder.apply(value.currency().trim())
            );
        }
    }

    /**
     * Rejects a {@link Long} field if it is less than or equal to zero.
     *
     * @param errors         Spring {@link Errors} collector.
     * @param value          Value to inspect.
     * @param field          Field name.
     * @param errorCode      Validation error code.
     * @param defaultMessage Human-readable error message.
     */
    public static void validatePositiveLong(
            Errors errors,
            Long value,
            String field,
            String errorCode,
            String defaultMessage
    ) {
        if (value != null && value <= 0) {
            errors.rejectValue(field, errorCode, defaultMessage);
        }
    }
}
