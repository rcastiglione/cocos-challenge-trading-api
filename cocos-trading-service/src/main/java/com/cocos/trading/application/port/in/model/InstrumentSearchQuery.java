package com.cocos.trading.application.port.in.model;

/**
 * Query model for searching market instruments.
 *
 * @param query  General search term matched against ticker or name.
 * @param ticker Specific ticker filter.
 * @param name   Specific instrument name filter.
 */
public record InstrumentSearchQuery(
        String query,
        String ticker,
        String name
) {
}

