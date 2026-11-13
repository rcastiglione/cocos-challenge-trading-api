package com.cocos.trading.repository.jpa;

import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link MarketDataJpaEntity}.
 */
@Repository
public interface SpringDataMarketDataRepository extends JpaRepository<MarketDataJpaEntity, Long> {

    /**
     * Finds the most recent market data entry for a given instrument ordered by date descending.
     *
     * @param instrumentId The instrument ID.
     * @return Optional containing the latest {@link MarketDataJpaEntity}.
     */
    Optional<MarketDataJpaEntity> findFirstByInstrumentIdOrderByDateDescIdDesc(Long instrumentId);

    /**
     * Finds all market data entries for a set of instrument IDs ordered by date descending.
     *
     * @param instrumentIds Collection of instrument IDs.
     * @return List of {@link MarketDataJpaEntity} ordered by date descending.
     */
    @Query("SELECT m FROM MarketDataJpaEntity m WHERE m.instrumentId IN :instrumentIds ORDER BY m.date DESC, m.id DESC")
    List<MarketDataJpaEntity> findAllByInstrumentIdsOrderByDateDesc(
            @Param("instrumentIds") Collection<Long> instrumentIds
    );
}

