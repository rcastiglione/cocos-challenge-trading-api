package com.cocos.trading.repository;

import com.cocos.trading.application.port.in.model.InstrumentSearchQuery;
import com.cocos.trading.application.port.out.InstrumentRepositoryPort;
import com.cocos.trading.domain.model.Instrument;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import com.cocos.trading.repository.jpa.InstrumentSpecifications;
import com.cocos.trading.repository.jpa.SpringDataInstrumentRepository;
import com.cocos.trading.repository.mapper.PersistenceMapper;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Outbound persistence adapter implementing {@link InstrumentRepositoryPort}.
 */
@Component
public class InstrumentRepositoryAdapter implements InstrumentRepositoryPort {

    private final SpringDataInstrumentRepository springDataInstrumentRepository;

    public InstrumentRepositoryAdapter(SpringDataInstrumentRepository springDataInstrumentRepository) {
        this.springDataInstrumentRepository = springDataInstrumentRepository;
    }

    @Override
    public Optional<Instrument> findById(Long id) {
        return springDataInstrumentRepository.findById(id)
                .map(PersistenceMapper::toDomain);
    }

    @Override
    public List<Instrument> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return springDataInstrumentRepository.findAllById(ids).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<Instrument> search(InstrumentSearchQuery query) {
        Specification<InstrumentJpaEntity> spec = InstrumentSpecifications.withFilters(query);
        Sort sort = Sort.by(Sort.Direction.ASC, "ticker");
        return springDataInstrumentRepository.findAll(spec, sort).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }
}

