package com.cocos.trading.repository.entity;

import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.OrderType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Concrete JPA entity representing a market trade order ({@link OrderSide#BUY}
 * or {@link OrderSide#SELL})
 * executed as {@link OrderType#MARKET} or {@link OrderType#LIMIT} on an equity
 * instrument ({@code ACCIONES}).
 */
@Entity
@DiscriminatorValue("TRADE")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class TradeOrderJpaEntity extends OrderJpaEntity {

    public TradeOrderJpaEntity(
            Long id,
            InstrumentJpaEntity instrument,
            UserJpaEntity user,
            Integer quantity,
            BigDecimal price,
            OrderType type,
            OrderSide side,
            OrderStatus status,
            LocalDateTime datetime) {
        super(id, instrument, user, quantity, price, type, side, status, datetime);
    }
}
