package com.cocos.trading.repository.mapper;

import com.cocos.trading.domain.model.Instrument;
import com.cocos.trading.domain.model.MarketData;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.Order;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.TradeOrder;
import com.cocos.trading.domain.model.TransferOrder;
import com.cocos.trading.domain.model.User;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.entity.UserJpaEntity;

/**
 * Anti-Corruption Layer (ACL) mapper converting between JPA persistence entities and pure domain models.
 *<ul>
 *   <li>Hydrates domain {@link MonetaryAmount} (with currency {@code "ARS"}) Value Objects
 *       on read, and unwraps raw scalar values on write, keeping the evaluator's PostgreSQL schema 100% intact.</li>
 *   <li>Acts as a polymorphic discriminator when translating between the unified {@link OrderJpaEntity}
 *       ledger table and the domain hierarchy ({@link TradeOrder} vs {@link TransferOrder}).</li>
 *</ul>
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
        return User.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .accountNumber(entity.getAccountNumber())
                .build();
    }

    /**
     * Converts an {@link InstrumentJpaEntity} to a domain {@link Instrument}.
     *
     * @param entity The JPA entity.
     * @return The domain {@link Instrument}.
     */
    public static Instrument toDomain(InstrumentJpaEntity entity) {
        return Instrument.builder()
                .id(entity.getId())
                .ticker(entity.getTicker())
                .name(entity.getName())
                .type(entity.getType())
                .build();
    }

    /**
     * Converts a {@link MarketDataJpaEntity} to a domain {@link MarketData}, wrapping raw database prices
     * into {@code ARS}-denominated {@link MonetaryAmount} Value Objects.
     *
     * @param entity The JPA entity.
     * @return The domain {@link MarketData}.
     */
    public static MarketData toDomain(MarketDataJpaEntity entity) {
        return MarketData.builder()
                .id(entity.getId())
                .instrumentId(entity.getInstrumentId())
                .high(MonetaryAmount.ars(entity.getHigh()))
                .low(MonetaryAmount.ars(entity.getLow()))
                .open(MonetaryAmount.ars(entity.getOpen()))
                .close(MonetaryAmount.ars(entity.getClose()))
                .previousClose(MonetaryAmount.ars(entity.getPreviousClose()))
                .date(entity.getDate())
                .build();
    }

    /**
     * Converts an {@link OrderJpaEntity} into its concrete polymorphic domain subclass
     * ({@link TradeOrder} or {@link TransferOrder}) using {@link OrderJpaEntity#getSide()} as discriminator,
     * wrapping the raw price into an {@code ARS} {@link MonetaryAmount}.
     *
     * @param entity The JPA entity from the unified {@code orders} table.
     * @return A {@link TradeOrder} for BUY/SELL operations, or a {@link TransferOrder} for CASH_IN/CASH_OUT movements.
     */
    public static Order toDomain(OrderJpaEntity entity) {
        OrderSide side = entity.getSide();
        MonetaryAmount price = MonetaryAmount.ars(entity.getPrice());
        if (side != null && side.isCashOperation()) {
            return TransferOrder.builder()
                    .id(entity.getId())
                    .instrumentId(entity.getInstrumentId())
                    .userId(entity.getUserId())
                    .size(entity.getSize())
                    .price(price)
                    .type(entity.getType())
                    .side(side)
                    .status(entity.getStatus())
                    .datetime(entity.getDatetime())
                    .build();
        }
        return TradeOrder.builder()
                .id(entity.getId())
                .instrumentId(entity.getInstrumentId())
                .userId(entity.getUserId())
                .size(entity.getSize())
                .price(price)
                .type(entity.getType())
                .side(side)
                .status(entity.getStatus())
                .datetime(entity.getDatetime())
                .build();
    }

    /**
     * Converts a polymorphic domain {@link Order} ({@link TradeOrder} or {@link TransferOrder})
     * to a unified {@link OrderJpaEntity}, unwrapping {@link MonetaryAmount#amount()} to preserve the fixed DB schema.
     *
     * @param domain The domain {@link Order}.
     * @return The corresponding {@link OrderJpaEntity}.
     */
    public static OrderJpaEntity toEntity(Order domain) {
        return OrderJpaEntity.builder()
                .id(domain.getId())
                .instrumentId(domain.getInstrumentId())
                .userId(domain.getUserId())
                .size(domain.getSize())
                .price(domain.getPrice() != null ? domain.getPrice().amount() : null)
                .type(domain.getType())
                .side(domain.getSide())
                .status(domain.getStatus())
                .datetime(domain.getDatetime())
                .build();
    }
}
