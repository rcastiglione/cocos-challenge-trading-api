package com.cocos.trading.controller;

import com.cocos.trading.api.PortfolioContractApi;
import com.cocos.trading.api.response.PortfolioApi;
import com.cocos.trading.application.port.in.GetPortfolioUseCase;
import com.cocos.trading.controller.mapper.ApiMapper;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller implementing {@link PortfolioContractApi}.
 */
@RestController
public class PortfolioController implements PortfolioContractApi {

    private final GetPortfolioUseCase getPortfolioUseCase;

    public PortfolioController(GetPortfolioUseCase getPortfolioUseCase) {
        this.getPortfolioUseCase = getPortfolioUseCase;
    }

    @Override
    public PortfolioApi getPortfolio(Long userId) {
        return ApiMapper.toApi(getPortfolioUseCase.getPortfolio(userId));
    }
}

