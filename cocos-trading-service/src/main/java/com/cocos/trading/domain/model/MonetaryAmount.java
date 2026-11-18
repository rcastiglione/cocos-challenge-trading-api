package com.cocos.trading.domain.model;

import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Builder;

/**
 * Rich Domain Value Object implementing the Money Pattern (Martin Fowler).
 * Encapsulates a monetary amount and its ISO-4217 currency code, enforcing scale normalization
 * and currency-safe arithmetic invariants across the trading domain.
 *
 * @param amount   Normalized monetary amount (scale 2, {@link RoundingMode#HALF_UP}).
 * @param currency Uppercase ISO-4217 3-letter currency code (e.g., {@code "ARS"}).
 */
@Builder
public record MonetaryAmount(BigDecimal amount, String currency) {

    public static final String ARS_CURRENCY = "ARS";
    public static final int CURRENCY_SCALE = 2;

    public static final MonetaryAmount ZERO_ARS = new MonetaryAmount(BigDecimal.ZERO, ARS_CURRENCY);
    public static final MonetaryAmount ONE_ARS = new MonetaryAmount(BigDecimal.ONE, ARS_CURRENCY);

    public MonetaryAmount {
        if (amount == null) {
            throw new BusinessRuleViolationException("MonetaryAmount value cannot be null.");
        }
        if (currency == null || currency.isBlank()) {
            throw new BusinessRuleViolationException("MonetaryAmount currency cannot be null or blank.");
        }
        amount = amount.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        currency = currency.trim().toUpperCase();
    }

    /**
     * Creates an {@code ARS}-denominated {@link MonetaryAmount}, or returns {@code null} if {@code amount} is {@code null}.
     *
     * @param amount The monetary value in Argentine Pesos.
     * @return The {@link MonetaryAmount} in {@code ARS}, or {@code null}.
     */
    public static MonetaryAmount ars(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return new MonetaryAmount(amount, ARS_CURRENCY);
    }

    /**
     * Convenience factory for creating an {@code ARS}-denominated {@link MonetaryAmount} from a string literal.
     *
     * @param amount Numeric string representation (e.g., {@code "925.85"}).
     * @return The {@link MonetaryAmount} in {@code ARS}.
     */
    public static MonetaryAmount ars(String amount) {
        if (amount == null) {
            return null;
        }
        return new MonetaryAmount(new BigDecimal(amount), ARS_CURRENCY);
    }

    /**
     * Creates a {@link MonetaryAmount} with explicit amount and currency.
     *
     * @param amount   Monetary value.
     * @param currency ISO-4217 currency code.
     * @return A validated {@link MonetaryAmount}.
     */
    public static MonetaryAmount of(BigDecimal amount, String currency) {
        return new MonetaryAmount(amount, currency);
    }

    /**
     * Adds another {@link MonetaryAmount} of the same currency.
     *
     * @param other Amount to add.
     * @return Sum in the same currency.
     */
    public MonetaryAmount add(MonetaryAmount other) {
        assertSameCurrency(other);
        return new MonetaryAmount(this.amount.add(other.amount), this.currency);
    }

    /**
     * Subtracts another {@link MonetaryAmount} of the same currency.
     *
     * @param other Amount to subtract.
     * @return Difference in the same currency.
     */
    public MonetaryAmount subtract(MonetaryAmount other) {
        assertSameCurrency(other);
        return new MonetaryAmount(this.amount.subtract(other.amount), this.currency);
    }

    /**
     * Multiplies this monetary amount by an integer share/unit quantity.
     *
     * @param quantity Share or unit quantity.
     * @return Multiplied {@link MonetaryAmount} in the same currency.
     */
    public MonetaryAmount multiply(long quantity) {
        return new MonetaryAmount(this.amount.multiply(BigDecimal.valueOf(quantity)), this.currency);
    }

    /**
     * Returns a new {@link MonetaryAmount} with the negated amount and identical currency.
     *
     * @return Negated {@link MonetaryAmount}.
     */
    public MonetaryAmount negate() {
        return new MonetaryAmount(this.amount.negate(), this.currency);
    }

    /**
     * Calculates the maximum whole number of shares/units that can be purchased with this total investment amount
     * at the given {@code unitPrice} ({@code floor(this.amount / unitPrice.amount)}).
     *
     * @param unitPrice Price per share/unit in the same currency.
     * @return Maximum integer number of whole shares.
     */
    public long divideToIntegralShares(MonetaryAmount unitPrice) {
        if (unitPrice == null) {
            return 0L;
        }
        assertSameCurrency(unitPrice);
        if (!this.isPositive() || !unitPrice.isPositive()) {
            return 0L;
        }
        return this.amount.divideToIntegralValue(unitPrice.amount).longValueExact();
    }

    /**
     * Checks whether this monetary amount is strictly greater than zero.
     *
     * @return {@code true} if {@code amount > 0}.
     */
    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Checks whether this monetary amount is less than or equal to zero.
     *
     * @return {@code true} if {@code amount <= 0}.
     */
    public boolean isZeroOrNegative() {
        return this.amount.compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * Checks whether this monetary amount is strictly less than {@code other}.
     *
     * @param other Monetary amount to compare against.
     * @return {@code true} if {@code this.amount < other.amount}.
     */
    public boolean isLessThan(MonetaryAmount other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    /**
     * Checks whether this monetary amount is strictly greater than {@code other}.
     *
     * @param other Monetary amount to compare against.
     * @return {@code true} if {@code this.amount > other.amount}.
     */
    public boolean isGreaterThan(MonetaryAmount other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    private void assertSameCurrency(MonetaryAmount other) {
        if (other == null) {
            throw new BusinessRuleViolationException("Cannot operate with a null MonetaryAmount.");
        }
        if (!this.currency.equals(other.currency)) {
            throw new BusinessRuleViolationException(
                    String.format("Currency mismatch in monetary operation: expected %s but received %s.",
                            this.currency, other.currency)
            );
        }
    }
}

