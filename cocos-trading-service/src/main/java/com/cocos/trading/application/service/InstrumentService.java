package com.cocos.trading.application.service;

import com.cocos.trading.application.port.in.SearchInstrumentsUseCase;
import com.cocos.trading.application.port.in.model.InstrumentSearchQuery;
import com.cocos.trading.application.port.out.InstrumentRepositoryPort;
import com.cocos.trading.application.port.out.MarketDataRepositoryPort;
import com.cocos.trading.domain.model.InstrumentQuote;
import com.cocos.trading.domain.model.InstrumentType;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service implementing {@link SearchInstrumentsUseCase}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentService implements SearchInstrumentsUseCase {

    private static final int CURRENCY_SCALE = MonetaryAmount.CURRENCY_SCALE;

    private final InstrumentRepositoryPort instrumentRepositoryPort;
    private final MarketDataRepositoryPort marketDataRepositoryPort;

    @Override
    @Transactional(readOnly = true)
    public List<InstrumentQuote> search(InstrumentSearchQuery query) {
        log.info("[instrument] Searching instruments with query={}", query);
        List<InstrumentJpaEntity> instruments = instrumentRepositoryPort.search(query);
        if (instruments.isEmpty()) {
            return List.of();
        }

        Set<Long> instrumentIds = instruments.stream()
                .map(InstrumentJpaEntity::getId)
                .collect(Collectors.toSet());

        Map<Long, MarketDataJpaEntity> marketDataById = marketDataRepositoryPort.findLatestByInstrumentIds(instrumentIds);

        return instruments.stream()
                .map(inst -> toInstrumentQuote(inst, marketDataById.get(inst.getId())))
                .toList();
    }

    private InstrumentQuote toInstrumentQuote(InstrumentJpaEntity instrument, MarketDataJpaEntity marketData) {
        boolean isCurrency = instrument.getType() == InstrumentType.MONEDA;
        if (marketData == null) {
            MonetaryAmount defaultPrice = isCurrency
                    ? MonetaryAmount.ONE_ARS
                    : null;
            BigDecimal zeroReturn = isCurrency
                    ? BigDecimal.ZERO.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP)
                    : null;
            return InstrumentQuote.builder()
                    .instrument(instrument)
                    .lastPrice(defaultPrice)
                    .previousClose(defaultPrice)
                    .dailyReturnPercentage(zeroReturn)
                    .build();
        }
        return InstrumentQuote.builder()
                .instrument(instrument)
                .lastPrice(MonetaryAmount.ars(marketData.getClose()))
                .previousClose(MonetaryAmount.ars(marketData.getPreviousClose()))
                .dailyReturnPercentage(AccountLedgerCalculator.calculateDailyReturnPercentage(marketData))
                .build();
    }
}
