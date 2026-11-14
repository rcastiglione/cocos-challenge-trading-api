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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Outbound persistence adapter implementing {@link InstrumentRepositoryPort}.
 */
@Component
@RequiredArgsConstructor
public class InstrumentRepositoryAdapter implements InstrumentRepositoryPort {

    private final SpringDataInstrumentRepository springDataInstrumentRepository;

    @Override
    public Optional<InstrumentJpaEntity> findById(Long id) {
        return springDataInstrumentRepository.findById(id);
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
        return springDataInstrumentRepository.findAll(spec, sort);
    }
}
