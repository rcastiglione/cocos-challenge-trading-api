package com.cocos.trading.repository.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Concrete JPA entity representing a cash transfer movement
 * ({@code CASH_IN} or {@code CASH_OUT}) on a currency instrument
 * ({@code MONEDA}).
 */
@Entity
@DiscriminatorValue("TRANSFER")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder(toBuilder = true)
public class TransferOrderJpaEntity extends OrderJpaEntity {
}
