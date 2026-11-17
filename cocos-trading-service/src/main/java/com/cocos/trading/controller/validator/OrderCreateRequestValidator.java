package com.cocos.trading.controller.validator;

import com.cocos.trading.api.common.OrderTypeApi;
import com.cocos.trading.api.request.OrderCreateRequestApi;
import com.cocos.trading.api.request.TradeOrderRequestApi;
import com.cocos.trading.api.request.TransferOrderRequestApi;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Spring {@link Validator} for the polymorphic {@link OrderCreateRequestApi} hierarchy
 * ({@link TradeOrderRequestApi} and {@link TransferOrderRequestApi}).
 * Uses Java 21 exhaustive pattern matching over the {@code sealed interface} to enforce subtype-specific invariants.
 */
public class OrderCreateRequestValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return OrderCreateRequestApi.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        OrderCreateRequestApi request = (OrderCreateRequestApi) target;

        ValidatorUtils.rejectIfNull(
                errors,
                request.userId(),
                "userId",
                "userId.required",
                "userId is required."
        );
        if (request.userId() != null && request.userId() <= 0) {
            errors.rejectValue("userId", "userId.invalid", "userId must be greater than 0.");
        }

        ValidatorUtils.rejectIfNull(
                errors,
                request.side(),
                "side",
                "side.required",
                "side is required."
        );

        switch (request) {
            case TradeOrderRequestApi tradeRequest -> validateTradeOrder(tradeRequest, errors);
            case TransferOrderRequestApi transferRequest -> validateTransferOrder(transferRequest, errors);
        }
    }

    private void validateTradeOrder(TradeOrderRequestApi request, Errors errors) {
        ValidatorUtils.rejectIfNull(
                errors,
                request.instrumentId(),
                "instrumentId",
                "instrumentId.required",
                "instrumentId is required."
        );
        if (request.instrumentId() != null && request.instrumentId() <= 0) {
            errors.rejectValue("instrumentId", "instrumentId.invalid", "instrumentId must be greater than 0.");
        }

        ValidatorUtils.rejectIfNull(
                errors,
                request.type(),
                "type",
                "type.required",
                "type is required."
        );

        boolean hasQuantity = request.quantity() != null;
        boolean hasAmount = request.amount() != null;

        if (!hasQuantity && !hasAmount) {
            errors.rejectValue(
                    "quantity",
                    "quantityOrAmount.required",
                    "Either quantity (exact shares) or amount (total investment in ARS) must be provided."
            );
        }

        if (hasQuantity && hasAmount) {
            errors.rejectValue(
                    "quantity",
                    "quantityOrAmount.ambiguous",
                    "Provide either quantity or amount, not both."
            );
        }

        ValidatorUtils.validatePositiveLong(
                errors,
                request.quantity(),
                "quantity",
                "quantity.invalid",
                "quantity must be greater than 0."
        );

        ValidatorUtils.validateMonetaryAmount(
                errors,
                request.amount(),
                "amount",
                "amount.invalid",
                "amount.amount must be greater than 0."
        );

        if (request.type() == OrderTypeApi.LIMIT) {
            if (request.price() == null) {
                errors.rejectValue(
                        "price",
                        "price.required",
                        "price is required for LIMIT orders."
                );
            } else {
                ValidatorUtils.validateMonetaryAmount(
                        errors,
                        request.price(),
                        "price",
                        "price.invalid",
                        "price.amount must be greater than 0."
                );
            }
        }
    }

    private void validateTransferOrder(TransferOrderRequestApi request, Errors errors) {
        if (request.amount() == null) {
            errors.rejectValue(
                    "amount",
                    "amount.required",
                    "amount is required for CASH_IN and CASH_OUT transfers."
            );
        } else {
            ValidatorUtils.validateMonetaryAmount(
                    errors,
                    request.amount(),
                    "amount",
                    "amount.invalid",
                    "amount.amount must be greater than 0.",
                    currency -> String.format(
                            "Currency '%s' is not supported. Only 'ARS' transfers are currently allowed.",
                            currency
                    )
            );
        }
    }
}
