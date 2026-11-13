package com.cocos.trading.controller;

import com.cocos.trading.api.OrderContractApi;
import com.cocos.trading.api.request.OrderCreateRequestApi;
import com.cocos.trading.api.response.OrderApi;
import com.cocos.trading.application.port.in.CancelOrderUseCase;
import com.cocos.trading.application.port.in.PlaceOrderUseCase;
import com.cocos.trading.controller.mapper.ApiMapper;
import com.cocos.trading.controller.validator.OrderCreateRequestValidator;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller implementing {@link OrderContractApi}.
 */
@RestController
public class OrderController implements OrderContractApi {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;

    public OrderController(
            PlaceOrderUseCase placeOrderUseCase,
            CancelOrderUseCase cancelOrderUseCase
    ) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
    }

    @InitBinder("orderCreateRequestApi")
    public void initBinder(WebDataBinder binder) {
        binder.addValidators(new OrderCreateRequestValidator());
    }

    @Override
    public OrderApi create(OrderCreateRequestApi createRequest) {
        return ApiMapper.toApi(placeOrderUseCase.placeOrder(ApiMapper.toCommand(createRequest)));
    }

    @Override
    public OrderApi cancel(Long orderId) {
        return ApiMapper.toApi(cancelOrderUseCase.cancelOrder(orderId));
    }
}

