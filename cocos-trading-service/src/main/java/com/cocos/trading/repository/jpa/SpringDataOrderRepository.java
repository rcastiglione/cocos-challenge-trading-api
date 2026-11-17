package com.cocos.trading.repository.jpa;

import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.entity.TradeOrderJpaEntity;
import com.cocos.trading.repository.entity.TransferOrderJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link OrderJpaEntity}.
 */
@Repository
public interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, Long> {

    /**
     * Retrieves an order by ID, eagerly fetching its associated {@code user} and {@code instrument} in a single query.
     *
     * @param id The order ID.
     * @return Optional containing the hydrated {@link OrderJpaEntity}.
     */
    @Override
    @EntityGraph(attributePaths = {"user", "instrument"})
    Optional<OrderJpaEntity> findById(Long id);

    /**
     * Retrieves an order by its unique client-provided idempotency key, eagerly fetching associated {@code user} and {@code instrument}.
     *
     * @param idempotencyKey Unique UUID key.
     * @return Optional containing the hydrated {@link OrderJpaEntity} if found.
     */
    @EntityGraph(attributePaths = {"user", "instrument"})
    Optional<OrderJpaEntity> findByIdempotencyKey(UUID idempotencyKey);

    /**
     * Retrieves all orders for a given user with a specific status, ordered chronologically by datetime and ID,
     * eagerly fetching the associated {@code user} and {@code instrument} entities.
     *
     * @param userId The user ID.
     * @param status The order status.
     * @return Chronologically ordered list of {@link OrderJpaEntity}.
     */
    @EntityGraph(attributePaths = {"user", "instrument"})
    List<OrderJpaEntity> findByUserIdAndStatusOrderByDatetimeAscIdAsc(Long userId, OrderStatus status);

    /**
     * Retrieves all concrete {@link TradeOrderJpaEntity} records for a user and status, ordered chronologically.
     *
     * @param userId The user ID.
     * @param status The order status.
     * @return Chronologically ordered list of {@link TradeOrderJpaEntity}.
     */
    @EntityGraph(attributePaths = {"user", "instrument"})
    @Query("SELECT t FROM TradeOrderJpaEntity t WHERE t.user.id = :userId AND t.status = :status ORDER BY t.datetime ASC, t.id ASC")
    List<TradeOrderJpaEntity> findTradeOrdersByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") OrderStatus status);

    /**
     * Retrieves all concrete {@link TransferOrderJpaEntity} records for a user and status, ordered chronologically.
     *
     * @param userId The user ID.
     * @param status The order status.
     * @return Chronologically ordered list of {@link TransferOrderJpaEntity}.
     */
    @EntityGraph(attributePaths = {"user", "instrument"})
    @Query("SELECT tr FROM TransferOrderJpaEntity tr WHERE tr.user.id = :userId AND tr.status = :status ORDER BY tr.datetime ASC, tr.id ASC")
    List<TransferOrderJpaEntity> findTransferOrdersByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") OrderStatus status);
}
