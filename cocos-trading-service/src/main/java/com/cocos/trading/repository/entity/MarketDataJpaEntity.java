package com.cocos.trading.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity mapped to the {@code marketdata} table.
 */
@Entity
@Table(name = "marketdata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketDataJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Column(name = "instrumentid", nullable = false)
    private Long instrumentId;

    @Column(name = "high")
    private BigDecimal high;

    @Column(name = "low")
    private BigDecimal low;

    @Column(name = "open")
    private BigDecimal open;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Column(name = "close", nullable = false)
    private BigDecimal close;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Column(name = "previousclose", nullable = false)
    private BigDecimal previousClose;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Column(name = "date", nullable = false)
    private LocalDate date;
}
