package com.cocos.trading.api.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Builder;

/**
 * Money Pattern DTO representing a monetary amount alongside its explicit ISO-4217 currency code.
 * Prevents currency mismatch ambiguity across financial API payloads.
 *
 * @param amount   Monetary value (e.g., {@code 925.85}).
 * @param currency ISO-4217 3-letter currency code (e.g., {@code "ARS"}).
 */
@Builder
@Schema(description = "Monetary amount paired with its ISO-4217 currency code")
public record MonetaryAmount(
        @Schema(description = "Monetary amount value", example = "925.85", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount,

        @Schema(description = "ISO-4217 currency code", example = "ARS", requiredMode = Schema.RequiredMode.REQUIRED)
        String currency
) {

    public static final String DEFAULT_CURRENCY = "ARS";

    /**
     * Convenience factory creating an {@code ARS}-denominated {@link MonetaryAmount}.
     *
     * @param amount The monetary value in Argentine Pesos.
     * @return A {@link MonetaryAmount} with currency {@code "ARS"}, or {@code null} if {@code amount} is {@code null}.
     */
    public static MonetaryAmount ars(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return new MonetaryAmount(amount, DEFAULT_CURRENCY);
    }
}

