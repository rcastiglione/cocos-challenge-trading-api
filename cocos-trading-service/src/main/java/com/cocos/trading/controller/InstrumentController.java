package com.cocos.trading.controller;

import com.cocos.trading.api.InstrumentContractApi;
import com.cocos.trading.api.request.InstrumentSearchFilterRequestApi;
import com.cocos.trading.api.response.InstrumentApi;
import com.cocos.trading.application.port.in.SearchInstrumentsUseCase;
import com.cocos.trading.controller.mapper.ApiMapper;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller implementing {@link InstrumentContractApi}.
 */
@RestController
public class InstrumentController implements InstrumentContractApi {

    private final SearchInstrumentsUseCase searchInstrumentsUseCase;

    public InstrumentController(SearchInstrumentsUseCase searchInstrumentsUseCase) {
        this.searchInstrumentsUseCase = searchInstrumentsUseCase;
    }

    @Override
    public List<InstrumentApi> search(InstrumentSearchFilterRequestApi filter) {
        return searchInstrumentsUseCase.search(ApiMapper.toQuery(filter)).stream()
                .map(ApiMapper::toApi)
                .toList();
    }
}

