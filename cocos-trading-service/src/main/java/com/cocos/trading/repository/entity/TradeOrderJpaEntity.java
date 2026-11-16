package com.cocos.trading.repository.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Concrete JPA entity representing a market trade order ({@code BUY} or
 * {@code SELL}) executed as {@code MARKET} or {@code LIMIT} on an equity
 * instrument ({@code ACCIONES}).
 */
@Entity
@DiscriminatorValue("TRADE")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder(toBuilder = true)
public class TradeOrderJpaEntity extends OrderJpaEntity {
}
