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

/**
 * JPA entity mapped to the {@code orders} table.
 */
@Entity
@Table(name = "orders")
public class OrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrumentid")
    private Long instrumentId;

    @Column(name = "userid")
    private Long userId;

    @Column(name = "size")
    private Integer size;

    @Column(name = "price")
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private OrderType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "side")
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status;

    @Column(name = "datetime")
    private LocalDateTime datetime;

    public OrderJpaEntity() {
    }

    public OrderJpaEntity(
            Long id,
            Long instrumentId,
            Long userId,
            Integer size,
            BigDecimal price,
            OrderType type,
            OrderSide side,
            OrderStatus status,
            LocalDateTime datetime
    ) {
        this.id = id;
        this.instrumentId = instrumentId;
        this.userId = userId;
        this.size = size;
        this.price = price;
        this.type = type;
        this.side = side;
        this.status = status;
        this.datetime = datetime;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public LocalDateTime getDatetime() {
        return datetime;
    }

    public void setDatetime(LocalDateTime datetime) {
        this.datetime = datetime;
    }
}

