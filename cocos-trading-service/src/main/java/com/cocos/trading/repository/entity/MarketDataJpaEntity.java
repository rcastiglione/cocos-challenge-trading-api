package com.cocos.trading.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * JPA entity mapped to the {@code marketdata} table.
 */
@Entity
@Table(name = "marketdata")
public class MarketDataJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrumentid")
    private Long instrumentId;

    @Column(name = "high")
    private BigDecimal high;

    @Column(name = "low")
    private BigDecimal low;

    @Column(name = "open")
    private BigDecimal open;

    @Column(name = "close")
    private BigDecimal close;

    @Column(name = "previousclose")
    private BigDecimal previousClose;

    @Column(name = "date")
    private LocalDate date;

    public MarketDataJpaEntity() {
    }

    public MarketDataJpaEntity(
            Long id,
            Long instrumentId,
            BigDecimal high,
            BigDecimal low,
            BigDecimal open,
            BigDecimal close,
            BigDecimal previousClose,
            LocalDate date
    ) {
        this.id = id;
        this.instrumentId = instrumentId;
        this.high = high;
        this.low = low;
        this.open = open;
        this.close = close;
        this.previousClose = previousClose;
        this.date = date;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public void setOpen(BigDecimal open) {
        this.open = open;
    }

    public BigDecimal getClose() {
        return close;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }

    public BigDecimal getPreviousClose() {
        return previousClose;
    }

    public void setPreviousClose(BigDecimal previousClose) {
        this.previousClose = previousClose;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }
}

