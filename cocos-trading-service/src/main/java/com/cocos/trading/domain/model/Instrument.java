package com.cocos.trading.domain.model;

import lombok.Builder;

/**
 * Domain entity representing a tradable financial instrument or currency.
 *
 * @param id     Unique identifier of the instrument.
 * @param ticker Ticker symbol (e.g., "PAMP", "ARS").
 * @param name   Full descriptive name of the instrument.
 * @param type   Type of the instrument ({@link InstrumentType#ACCIONES} or {@link InstrumentType#MONEDA}).
 */
@Builder
public record Instrument(
        Long id,
        String ticker,
        String name,
        InstrumentType type
) {
    /**
     * Checks whether this instrument represents cash currency (ARS).
     *
     * @return {@code true} if the instrument type is {@link InstrumentType#MONEDA}.
     */
    public boolean isCurrency() {
        return type == InstrumentType.MONEDA;
    }
}
