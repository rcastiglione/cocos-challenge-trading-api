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
     * <ul>
     *   <li>If {@code query} is provided, matches instruments where {@code ticker} OR {@code name} contains the term (case-insensitive).</li>
     *   <li>If {@code ticker} is provided, matches instruments where {@code ticker} equals the uppercase-normalized value (exact match).</li>
     *   <li>If {@code name} is provided, matches instruments where {@code name} equals the value (exact match, case-insensitive).</li>
     * </ul>
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
                String normalizedTicker = searchQuery.ticker().trim().toUpperCase();
                predicates.add(cb.equal(root.get("ticker"), normalizedTicker));
            }

            if (searchQuery.name() != null && !searchQuery.name().isBlank()) {
                String normalizedName = searchQuery.name().trim().toLowerCase();
                predicates.add(cb.equal(cb.lower(root.get("name")), normalizedName));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

