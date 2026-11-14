package com.cocos.trading.repository.jpa;

import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link InstrumentJpaEntity}.
 */
@Repository
public interface SpringDataInstrumentRepository
        extends JpaRepository<InstrumentJpaEntity, Long>, JpaSpecificationExecutor<InstrumentJpaEntity> {

    /**
     * Finds an instrument by its ticker symbol (case-insensitive).
     *
     * @param ticker The instrument ticker (e.g., {@code "ARS"} or {@code "PAMP"}).
     * @return An {@link Optional} containing the matching {@link InstrumentJpaEntity} if found.
     */
    Optional<InstrumentJpaEntity> findByTickerIgnoreCase(String ticker);
}
