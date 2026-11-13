package com.cocos.trading.repository.entity;

import com.cocos.trading.domain.model.InstrumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity mapped to the {@code instruments} table.
 */
@Entity
@Table(name = "instruments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstrumentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Column(name = "ticker", nullable = false)
    private String ticker;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Column(name = "name", nullable = false)
    private String name;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private InstrumentType type;
}
