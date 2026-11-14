package com.cocos.trading.repository.jpa;

import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Retrieves all orders for a given user with a specific status, ordered chronologically by datetime and ID,
     * eagerly fetching the associated {@code user} and {@code instrument} entities.
     *
     * @param userId The user ID.
     * @param status The order status.
     * @return Chronologically ordered list of {@link OrderJpaEntity}.
     */
    @EntityGraph(attributePaths = {"user", "instrument"})
    List<OrderJpaEntity> findByUserIdAndStatusOrderByDatetimeAscIdAsc(Long userId, OrderStatus status);
}
