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
 * Concrete JPA entity representing a cash transfer movement
 * ({@link OrderSide#CASH_IN} or {@link OrderSide#CASH_OUT})
 * on a currency instrument ({@code MONEDA}).
 */
@Entity
@DiscriminatorValue("TRANSFER")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class TransferOrderJpaEntity extends OrderJpaEntity {

    public TransferOrderJpaEntity(
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
