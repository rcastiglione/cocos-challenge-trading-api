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

/**
 * JPA entity mapped to the {@code instruments} table.
 */
@Entity
@Table(name = "instruments")
public class InstrumentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker")
    private String ticker;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private InstrumentType type;

    public InstrumentJpaEntity() {
    }

    public InstrumentJpaEntity(Long id, String ticker, String name, InstrumentType type) {
        this.id = id;
        this.ticker = ticker;
        this.name = name;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public InstrumentType getType() {
        return type;
    }

    public void setType(InstrumentType type) {
        this.type = type;
    }
}

