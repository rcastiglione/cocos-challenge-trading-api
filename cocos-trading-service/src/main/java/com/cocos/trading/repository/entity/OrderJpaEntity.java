package com.cocos.trading.repository.entity;

import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.OrderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DiscriminatorFormula;

/**
 * Abstract base JPA entity mapped to the unified {@code orders} table using
 * {@link InheritanceType#SINGLE_TABLE}.
 * <ul>
 * <li>Uses {@link DiscriminatorFormula} over the existing {@code side} column
 * to instantiate
 * {@link TradeOrderJpaEntity} ({@code BUY}/{@code SELL}) or
 * {@link TransferOrderJpaEntity}
 * ({@code CASH_IN}/{@code CASH_OUT}) with zero DDL schema mutation.</li>
 * <li>Models foreign keys as object-oriented {@link ManyToOne} relationships to
 * {@link UserJpaEntity}
 * and {@link InstrumentJpaEntity}.</li>
 * <li>Enforces distributed idempotency and safe replay via unique
 * {@code idempotency_key}.</li>
 * </ul>
 */
@Entity
@Table(name = "orders")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorFormula("CASE WHEN side IN ('CASH_IN', 'CASH_OUT') THEN 'TRANSFER' ELSE 'TRADE' END")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder(toBuilder = true)
public abstract class OrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", unique = true, updatable = false)
    private UUID idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrumentid", nullable = false)
    private InstrumentJpaEntity instrument;

    // TODO: The underlying DB schema currently allows NULLs for this column.
    // Pending a Flyway/Liquibase migration to enforce NOT NULL at the database
    // level.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "userid", nullable = false)
    private UserJpaEntity user;

    // TODO: The underlying DB schema currently allows NULLs for this column.
    // Pending a Flyway/Liquibase migration to enforce NOT NULL at the database
    // level.
    @Column(name = "size", nullable = false)
    private Long quantity;

    // TODO: The underlying DB schema currently allows NULLs for this column.
    // Pending a Flyway/Liquibase migration to enforce NOT NULL at the database
    // level.
    @Column(name = "price", nullable = false)
    private BigDecimal price;

    // TODO: The underlying DB schema currently allows NULLs for this column.
    // Pending a Flyway/Liquibase migration to enforce NOT NULL at the database
    // level.
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private OrderType type;

    // TODO: The underlying DB schema currently allows NULLs for this column.
    // Pending a Flyway/Liquibase migration to enforce NOT NULL at the database
    // level.
    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false)
    private OrderSide side;

    // TODO: The underlying DB schema currently allows NULLs for this column.
    // Pending a Flyway/Liquibase migration to enforce NOT NULL at the database
    // level.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    // TODO: The underlying DB schema currently allows NULLs for this column.
    // Pending a Flyway/Liquibase migration to enforce NOT NULL at the database
    // level.
    @Column(name = "datetime", nullable = false)
    private LocalDateTime datetime;

    @Column(name = "request_hash", length = 256, updatable = false)
    private String requestHash;
}
