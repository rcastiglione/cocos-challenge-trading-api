package com.cocos.trading.repository;

import com.cocos.trading.application.port.in.model.InstrumentSearchQuery;
import com.cocos.trading.application.port.out.InstrumentRepositoryPort;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import com.cocos.trading.repository.jpa.InstrumentSpecifications;
import com.cocos.trading.repository.jpa.SpringDataInstrumentRepository;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Outbound persistence adapter implementing {@link InstrumentRepositoryPort}.
 */
@Component
@RequiredArgsConstructor
public class InstrumentRepositoryAdapter implements InstrumentRepositoryPort {

    public static final int MAX_SEARCH_RESULTS = 50;

    private final SpringDataInstrumentRepository springDataInstrumentRepository;

    @Override
    public Optional<InstrumentJpaEntity> findById(Long id) {
        return springDataInstrumentRepository.findById(id);
    }

    @Override
    public Optional<InstrumentJpaEntity> findByTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return Optional.empty();
        }
        return springDataInstrumentRepository.findByTicker(ticker.trim().toUpperCase());
    }

    @Override
    public List<InstrumentJpaEntity> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return springDataInstrumentRepository.findAllById(ids);
    }

    @Override
    public List<InstrumentJpaEntity> search(InstrumentSearchQuery query) {
        Specification<InstrumentJpaEntity> spec = InstrumentSpecifications.withFilters(query);
        Sort sort = Sort.by(Sort.Direction.ASC, "ticker");
        return springDataInstrumentRepository
                .findAll(spec, PageRequest.of(0, MAX_SEARCH_RESULTS, sort))
                .getContent();
    }
}
