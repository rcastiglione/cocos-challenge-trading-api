package com.cocos.trading.application.service;

import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.domain.model.MonetaryAmount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes a deterministic SHA-256 fingerprint of an incoming
 * {@link PlaceOrderCommand} payload for Stripe-style idempotency mismatch
 * detection.
 */
final class RequestHashUtils {

    private RequestHashUtils() {
    }

    static String sha256(PlaceOrderCommand command) {
        String canonicalPayload = String.join("|",
                String.valueOf(command.userId()),
                String.valueOf(command.instrumentId()),
                String.valueOf(command.side()),
                String.valueOf(command.type()),
                String.valueOf(command.quantity()),
                formatMonetaryAmount(command.amount()),
                formatMonetaryAmount(command.price())
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private static String formatMonetaryAmount(MonetaryAmount monetaryAmount) {
        if (monetaryAmount == null) {
            return "null";
        }
        String amountStr = monetaryAmount.amount() != null ? monetaryAmount.amount().toPlainString() : "null";
        String currencyStr = monetaryAmount.currency() != null ? monetaryAmount.currency() : "null";
        return amountStr + ":" + currencyStr;
    }
}
