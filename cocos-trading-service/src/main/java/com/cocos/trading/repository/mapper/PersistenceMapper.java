package com.cocos.trading.repository.mapper;

import com.cocos.trading.domain.model.Instrument;
import com.cocos.trading.domain.model.MarketData;
import com.cocos.trading.domain.model.Order;
import com.cocos.trading.domain.model.User;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.entity.UserJpaEntity;

/**
 * Utility mapper class for converting between JPA persistence entities and pure domain models.
 */
public final class PersistenceMapper {

    private PersistenceMapper() {
    }

    /**
     * Converts a {@link UserJpaEntity} to a domain {@link User}.
     *
     * @param entity The JPA entity.
     * @return The domain {@link User}.
     */
    public static User toDomain(UserJpaEntity entity) {
        return new User(
                entity.getId(),
                entity.getEmail(),
                entity.getAccountNumber()
        );
    }

    /**
     * Converts an {@link InstrumentJpaEntity} to a domain {@link Instrument}.
     *
     * @param entity The JPA entity.
     * @return The domain {@link Instrument}.
     */
    public static Instrument toDomain(InstrumentJpaEntity entity) {
        return new Instrument(
                entity.getId(),
                entity.getTicker(),
                entity.getName(),
                entity.getType()
        );
    }

    /**
     * Converts a {@link MarketDataJpaEntity} to a domain {@link MarketData}.
     *
     * @param entity The JPA entity.
     * @return The domain {@link MarketData}.
     */
    public static MarketData toDomain(MarketDataJpaEntity entity) {
        return new MarketData(
                entity.getId(),
                entity.getInstrumentId(),
                entity.getHigh(),
                entity.getLow(),
                entity.getOpen(),
                entity.getClose(),
                entity.getPreviousClose(),
                entity.getDate()
        );
    }

    /**
     * Converts an {@link OrderJpaEntity} to a domain {@link Order}.
     *
     * @param entity The JPA entity.
     * @return The domain {@link Order}.
     */
    public static Order toDomain(OrderJpaEntity entity) {
        return new Order(
                entity.getId(),
                entity.getInstrumentId(),
                entity.getUserId(),
                entity.getSize(),
                entity.getPrice(),
                entity.getType(),
                entity.getSide(),
                entity.getStatus(),
                entity.getDatetime()
        );
    }

    /**
     * Converts a domain {@link Order} to an {@link OrderJpaEntity}.
     *
     * @param domain The domain {@link Order}.
     * @return The corresponding {@link OrderJpaEntity}.
     */
    public static OrderJpaEntity toEntity(Order domain) {
        return new OrderJpaEntity(
                domain.id(),
                domain.instrumentId(),
                domain.userId(),
                domain.size(),
                domain.price(),
                domain.type(),
                domain.side(),
                domain.status(),
                domain.datetime()
        );
    }
}

