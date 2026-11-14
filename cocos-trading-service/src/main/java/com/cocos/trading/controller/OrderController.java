package com.cocos.trading.controller;

import com.cocos.trading.api.OrderContractApi;
import com.cocos.trading.api.request.OrderCreateRequestApi;
import com.cocos.trading.api.response.OrderApi;
import com.cocos.trading.application.port.in.CancelOrderUseCase;
import com.cocos.trading.application.port.in.PlaceOrderUseCase;
import com.cocos.trading.controller.mapper.ApiMapper;
import com.cocos.trading.controller.validator.OrderCreateRequestValidator;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller implementing {@link OrderContractApi}.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController implements OrderContractApi {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        if (binder.getTarget() != null && OrderCreateRequestApi.class.isAssignableFrom(binder.getTarget().getClass())) {
            binder.addValidators(new OrderCreateRequestValidator());
        }
    }

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderApi create(
            @RequestHeader(OrderContractApi.IDEMPOTENCY_KEY_HEADER) UUID idempotencyKey,
            @Valid @RequestBody OrderCreateRequestApi createRequest
    ) {
        return ApiMapper.toApi(placeOrderUseCase.placeOrder(ApiMapper.toCommand(idempotencyKey, createRequest)));
    }

    @Override
    @PostMapping("/{orderId}/cancel")
    public OrderApi cancel(@PathVariable("orderId") Long orderId) {
        return ApiMapper.toApi(cancelOrderUseCase.cancelOrder(orderId));
    }
}
