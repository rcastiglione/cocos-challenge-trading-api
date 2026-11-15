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

    // =================================================================================================================
    // TODO [Database Schema & Idempotency Architecture]:
    // In a real production environment, we would add an `idempotency_key UUID NOT
    // NULL` column with a strict database
    // constraint `CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key)`
    // directly to the `orders` table in
    // PostgreSQL via a controlled Flyway/Liquibase migration as the ultimate ACID
    // safety net.
    // Because we must strictly respect the fixed relational database schema
    // provided by the challenge evaluator
    // (NeonDB / init.sql), we enforce idempotency and distributed locking at the
    // application/infrastructure boundary
    // using Redis (`SET idempotency:orders:<uuid> ... NX EX 86400` with a 24-hour
    // TTL).
    // =================================================================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO: The underlying DB schema currently allows NULLs for this column.
    // Pending a Flyway/Liquibase migration to enforce NOT NULL at the database
    // level.
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
    private Integer quantity;

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
}
