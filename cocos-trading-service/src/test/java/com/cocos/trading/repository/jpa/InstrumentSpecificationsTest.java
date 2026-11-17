package com.cocos.trading.repository.jpa;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocos.trading.application.port.in.model.InstrumentSearchQuery;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

@DisplayName("Instrument Specifications Unit Tests")
class InstrumentSpecificationsTest {

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should use exact uppercase match without LOWER() when ticker is provided")
    void shouldUseExactUppercaseMatchForTicker() {
        Root<InstrumentJpaEntity> root = mock(Root.class);
        CriteriaQuery<?> cq = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> tickerPath = mock(Path.class);
        Predicate eqPredicate = mock(Predicate.class);

        when(root.get("ticker")).thenReturn(tickerPath);
        when(cb.equal(tickerPath, "PAMP")).thenReturn(eqPredicate);

        Specification<InstrumentJpaEntity> spec =
                InstrumentSpecifications.withFilters(new InstrumentSearchQuery(null, " pamp ", null));
        spec.toPredicate(root, cq, cb);

        verify(cb).equal(tickerPath, "PAMP");
        verify(cb, never()).lower(any());
        verify(cb, never()).like(any(Expression.class), any(String.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should use exact case-insensitive match when name is provided")
    void shouldUseExactLowercaseMatchForName() {
        Root<InstrumentJpaEntity> root = mock(Root.class);
        CriteriaQuery<?> cq = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<String> namePath = mock(Path.class);
        Expression<String> lowerName = mock(Expression.class);
        Predicate eqPredicate = mock(Predicate.class);

        when(root.<String>get("name")).thenReturn(namePath);
        when(cb.lower(namePath)).thenReturn(lowerName);
        when(cb.equal(lowerName, "molinos río de la plata")).thenReturn(eqPredicate);

        Specification<InstrumentJpaEntity> spec =
                InstrumentSpecifications.withFilters(new InstrumentSearchQuery(null, null, " Molinos Río de la Plata "));
        spec.toPredicate(root, cq, cb);

        verify(cb).lower(namePath);
        verify(cb).equal(lowerName, "molinos río de la plata");
        verify(cb, never()).like(any(Expression.class), any(String.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should use wildcard LIKE on ticker and name when broad query is provided")
    void shouldUseWildcardLikeForBroadQuery() {
        Root<InstrumentJpaEntity> root = mock(Root.class);
        CriteriaQuery<?> cq = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<String> tickerPath = mock(Path.class);
        Path<String> namePath = mock(Path.class);
        Expression<String> lowerTicker = mock(Expression.class);
        Expression<String> lowerName = mock(Expression.class);
        Predicate tickerLike = mock(Predicate.class);
        Predicate nameLike = mock(Predicate.class);

        when(root.<String>get("ticker")).thenReturn(tickerPath);
        when(root.<String>get("name")).thenReturn(namePath);
        when(cb.lower(tickerPath)).thenReturn(lowerTicker);
        when(cb.lower(namePath)).thenReturn(lowerName);
        when(cb.like(lowerTicker, "%pamp%")).thenReturn(tickerLike);
        when(cb.like(lowerName, "%pamp%")).thenReturn(nameLike);

        Specification<InstrumentJpaEntity> spec =
                InstrumentSpecifications.withFilters(new InstrumentSearchQuery(" PAMP ", null, null));
        spec.toPredicate(root, cq, cb);

        verify(cb).like(lowerTicker, "%pamp%");
        verify(cb).like(lowerName, "%pamp%");
    }
}

