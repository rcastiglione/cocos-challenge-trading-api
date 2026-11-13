package com.cocos.trading.repository.entity;

import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.OrderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity mapped to the {@code orders} table.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Column(name = "instrumentid", nullable = false)
    private Long instrumentId;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Column(name = "userid", nullable = false)
    private Long userId;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Column(name = "size", nullable = false)
    private Integer size;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Column(name = "price", nullable = false)
    private BigDecimal price;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private OrderType type;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false)
    private OrderSide side;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Column(name = "datetime", nullable = false)
    private LocalDateTime datetime;
}
