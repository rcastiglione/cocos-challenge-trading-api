package com.cocos.trading.repository.jpa;

import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link InstrumentJpaEntity}.
 */
@Repository
public interface SpringDataInstrumentRepository
        extends JpaRepository<InstrumentJpaEntity, Long>, JpaSpecificationExecutor<InstrumentJpaEntity> {
}

