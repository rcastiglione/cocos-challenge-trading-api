package com.cocos.trading.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.cocos.trading.application.port.in.model.InstrumentSearchQuery;
import com.cocos.trading.application.port.out.InstrumentRepositoryPort;
import com.cocos.trading.application.port.out.MarketDataRepositoryPort;
import com.cocos.trading.domain.model.InstrumentQuote;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.fixture.TradingFixtures;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Instrument Service Unit Tests")
class InstrumentServiceTest {

    @Mock
    private InstrumentRepositoryPort instrumentRepositoryPort;

    @Mock
    private MarketDataRepositoryPort marketDataRepositoryPort;

    @InjectMocks
    private InstrumentService instrumentService;

    @Test
    @DisplayName("Should search instruments and enrich them with latest market data")
    void shouldSearchAndEnrichInstruments() {
        InstrumentSearchQuery query = new InstrumentSearchQuery("PAMP", null, null);
        when(instrumentRepositoryPort.search(query)).thenReturn(List.of(TradingFixtures.pampInstrument()));
        when(marketDataRepositoryPort.findLatestByInstrumentIds(Set.of(TradingFixtures.PAMP_INSTRUMENT_ID)))
                .thenReturn(Map.of(TradingFixtures.PAMP_INSTRUMENT_ID, TradingFixtures.pampMarketData()));

        List<InstrumentQuote> results = instrumentService.search(query);

        assertEquals(1, results.size());
        InstrumentQuote quote = results.getFirst();
        assertEquals("PAMP", quote.instrument().getTicker());
        assertEquals(MonetaryAmount.ars("925.85"), quote.lastPrice());
        assertEquals(MonetaryAmount.ars("906.35"), quote.previousClose());
        assertEquals(new BigDecimal("2.15"), quote.dailyReturnPercentage());
    }
}
