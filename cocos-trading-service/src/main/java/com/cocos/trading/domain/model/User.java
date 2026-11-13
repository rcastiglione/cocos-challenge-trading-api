package com.cocos.trading.domain.model;

/**
 * Domain entity representing a user of the trading platform.
 *
 * @param id            Unique identifier of the user.
 * @param email         Email address of the user.
 * @param accountNumber Trading account number.
 */
public record User(
        Long id,
        String email,
        String accountNumber
) {
}

