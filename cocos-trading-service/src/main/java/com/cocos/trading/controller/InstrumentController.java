package com.cocos.trading.controller;

import com.cocos.trading.api.InstrumentContractApi;
import com.cocos.trading.api.request.InstrumentSearchFilterRequestApi;
import com.cocos.trading.api.response.InstrumentApi;
import com.cocos.trading.application.port.in.SearchInstrumentsUseCase;
import com.cocos.trading.controller.mapper.ApiMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller implementing {@link InstrumentContractApi}.
 */
@RestController
@RequestMapping("/instruments")
@RequiredArgsConstructor
public class InstrumentController implements InstrumentContractApi {

    private final SearchInstrumentsUseCase searchInstrumentsUseCase;

    @Override
    @GetMapping
    public List<InstrumentApi> search(@ModelAttribute InstrumentSearchFilterRequestApi filter) {
        return searchInstrumentsUseCase.search(ApiMapper.toQuery(filter)).stream()
                .map(ApiMapper::toApi)
                .toList();
    }
}
