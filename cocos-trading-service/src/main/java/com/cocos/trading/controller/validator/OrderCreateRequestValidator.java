package com.cocos.trading.controller.validator;

import com.cocos.trading.api.common.OrderTypeApi;
import com.cocos.trading.api.request.OrderCreateRequestApi;
import java.math.BigDecimal;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Spring {@link Validator} for {@link OrderCreateRequestApi}.
 * Enforces required fields, positive quantities/amounts, and LIMIT order price rules.
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
                request.side(),
                "side",
                "side.required",
                "side is required."
        );

        ValidatorUtils.rejectIfNull(
                errors,
                request.type(),
                "type",
                "type.required",
                "type is required."
        );

        boolean hasSize = request.size() != null;
        boolean hasAmount = request.amount() != null;

        if (!hasSize && !hasAmount) {
            errors.rejectValue(
                    "size",
                    "sizeOrAmount.required",
                    "Either size (exact shares) or amount (total investment in ARS) must be provided."
            );
        }

        if (hasSize && hasAmount) {
            errors.rejectValue(
                    "size",
                    "sizeOrAmount.ambiguous",
                    "Provide either size or amount, not both."
            );
        }

        ValidatorUtils.validatePositiveInteger(
                errors,
                request.size(),
                "size",
                "size.invalid",
                "size must be greater than 0."
        );

        ValidatorUtils.validatePositiveBigDecimal(
                errors,
                request.amount(),
                "amount",
                "amount.invalid",
                "amount must be greater than 0."
        );

        if (request.type() == OrderTypeApi.LIMIT) {
            if (request.price() == null) {
                errors.rejectValue(
                        "price",
                        "price.required",
                        "price is required for LIMIT orders."
                );
            } else if (request.price().compareTo(BigDecimal.ZERO) <= 0) {
                errors.rejectValue(
                        "price",
                        "price.invalid",
                        "price must be greater than 0."
                );
            }
        }
    }
}

