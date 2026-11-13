package com.cocos.trading.repository;

import com.cocos.trading.application.port.out.MarketDataRepositoryPort;
import com.cocos.trading.domain.model.MarketData;
import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import com.cocos.trading.repository.jpa.SpringDataMarketDataRepository;
import com.cocos.trading.repository.mapper.PersistenceMapper;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Outbound persistence adapter implementing {@link MarketDataRepositoryPort}.
 */
@Component
@RequiredArgsConstructor
public class MarketDataRepositoryAdapter implements MarketDataRepositoryPort {

    private final SpringDataMarketDataRepository springDataMarketDataRepository;

    @Override
    public Optional<MarketData> findLatestByInstrumentId(Long instrumentId) {
        return springDataMarketDataRepository.findFirstByInstrumentIdOrderByDateDescIdDesc(instrumentId)
                .map(PersistenceMapper::toDomain);
    }

    @Override
    public Map<Long, MarketData> findLatestByInstrumentIds(Collection<Long> instrumentIds) {
        if (instrumentIds == null || instrumentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MarketDataJpaEntity> allEntries =
                springDataMarketDataRepository.findAllByInstrumentIdsOrderByDateDesc(instrumentIds);

        Map<Long, MarketData> latestByInstrument = new LinkedHashMap<>();
        for (MarketDataJpaEntity entity : allEntries) {
            latestByInstrument.putIfAbsent(entity.getInstrumentId(), PersistenceMapper.toDomain(entity));
        }
        return latestByInstrument;
    }
}
