package com.cocos.trading.controller;

import com.cocos.trading.api.PortfolioContractApi;
import com.cocos.trading.api.response.PortfolioApi;
import com.cocos.trading.application.port.in.GetPortfolioUseCase;
import com.cocos.trading.controller.mapper.ApiMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller implementing {@link PortfolioContractApi}.
 */
@RestController
@RequestMapping("/portfolios")
@RequiredArgsConstructor
public class PortfolioController implements PortfolioContractApi {

    private final GetPortfolioUseCase getPortfolioUseCase;

    @Override
    @GetMapping("/{userId}")
    public PortfolioApi getPortfolio(@PathVariable("userId") Long userId) {
        return ApiMapper.toApi(getPortfolioUseCase.getPortfolio(userId));
    }
}
