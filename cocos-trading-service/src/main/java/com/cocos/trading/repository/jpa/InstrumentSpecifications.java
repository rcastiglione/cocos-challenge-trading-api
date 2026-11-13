package com.cocos.trading.repository.jpa;

import com.cocos.trading.application.port.in.model.InstrumentSearchQuery;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA {@link Specification} builder for filtering {@link InstrumentJpaEntity} records by ticker and/or name.
 */
public final class InstrumentSpecifications {

    private InstrumentSpecifications() {
    }

    /**
     * Builds a dynamic JPA {@link Specification} from the given {@link InstrumentSearchQuery}.
     *<ul>
     *   <li>If {@code query} is provided, matches instruments where {@code ticker} OR {@code name} contains the term (case-insensitive).</li>
     *   <li>If {@code ticker} is provided, matches instruments where {@code ticker} contains the value (case-insensitive).</li>
     *   <li>If {@code name} is provided, matches instruments where {@code name} contains the value (case-insensitive).</li>
     *</ul>
     *
     * @param searchQuery The filter criteria.
     * @return The composed {@link Specification}.
     */
    public static Specification<InstrumentJpaEntity> withFilters(InstrumentSearchQuery searchQuery) {
        return (root, query, cb) -> {
            if (searchQuery == null) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            if (searchQuery.query() != null && !searchQuery.query().isBlank()) {
                String pattern = "%" + searchQuery.query().trim().toLowerCase() + "%";
                Predicate tickerMatch = cb.like(cb.lower(root.get("ticker")), pattern);
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                predicates.add(cb.or(tickerMatch, nameMatch));
            }

            if (searchQuery.ticker() != null && !searchQuery.ticker().isBlank()) {
                String pattern = "%" + searchQuery.ticker().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("ticker")), pattern));
            }

            if (searchQuery.name() != null && !searchQuery.name().isBlank()) {
                String pattern = "%" + searchQuery.name().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

