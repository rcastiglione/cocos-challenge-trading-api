package com.cocos.trading.application.service;

import com.cocos.trading.application.port.in.SearchInstrumentsUseCase;
import com.cocos.trading.application.port.in.model.InstrumentSearchQuery;
import com.cocos.trading.application.port.out.InstrumentRepositoryPort;
import com.cocos.trading.application.port.out.MarketDataRepositoryPort;
import com.cocos.trading.domain.model.Instrument;
import com.cocos.trading.domain.model.InstrumentQuote;
import com.cocos.trading.domain.model.MarketData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service implementing {@link SearchInstrumentsUseCase}.
 */
@Service
public class InstrumentService implements SearchInstrumentsUseCase {

    private static final Logger log = LoggerFactory.getLogger(InstrumentService.class);
    private static final int CURRENCY_SCALE = 2;

    private final InstrumentRepositoryPort instrumentRepositoryPort;
    private final MarketDataRepositoryPort marketDataRepositoryPort;

    public InstrumentService(
            InstrumentRepositoryPort instrumentRepositoryPort,
            MarketDataRepositoryPort marketDataRepositoryPort
    ) {
        this.instrumentRepositoryPort = instrumentRepositoryPort;
        this.marketDataRepositoryPort = marketDataRepositoryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstrumentQuote> search(InstrumentSearchQuery query) {
        log.info("[instrument] Searching instruments with query={}", query);
        List<Instrument> instruments = instrumentRepositoryPort.search(query);
        if (instruments.isEmpty()) {
            return List.of();
        }

        Set<Long> instrumentIds = instruments.stream()
                .map(Instrument::id)
                .collect(Collectors.toSet());

        Map<Long, MarketData> marketDataById = marketDataRepositoryPort.findLatestByInstrumentIds(instrumentIds);

        return instruments.stream()
                .map(inst -> toInstrumentQuote(inst, marketDataById.get(inst.id())))
                .toList();
    }

    private InstrumentQuote toInstrumentQuote(Instrument instrument, MarketData marketData) {
        if (marketData == null) {
            BigDecimal defaultPrice = instrument.isCurrency()
                    ? BigDecimal.ONE.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP)
                    : null;
            BigDecimal zeroReturn = instrument.isCurrency()
                    ? BigDecimal.ZERO.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP)
                    : null;
            return new InstrumentQuote(instrument, defaultPrice, defaultPrice, zeroReturn);
        }
        BigDecimal close = marketData.close() != null
                ? marketData.close().setScale(CURRENCY_SCALE, RoundingMode.HALF_UP)
                : null;
        BigDecimal previousClose = marketData.previousClose() != null
                ? marketData.previousClose().setScale(CURRENCY_SCALE, RoundingMode.HALF_UP)
                : null;
        return new InstrumentQuote(
                instrument,
                close,
                previousClose,
                marketData.calculateDailyReturnPercentage()
        );
    }
}

