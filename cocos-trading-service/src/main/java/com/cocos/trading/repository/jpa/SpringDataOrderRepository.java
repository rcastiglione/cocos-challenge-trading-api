package com.cocos.trading.repository.jpa;

import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link OrderJpaEntity}.
 */
@Repository
public interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, Long> {

    /**
     * Retrieves all orders for a given user with a specific status, ordered chronologically by datetime and ID.
     *
     * @param userId The user ID.
     * @param status The order status.
     * @return Chronologically ordered list of {@link OrderJpaEntity}.
     */
    List<OrderJpaEntity> findByUserIdAndStatusOrderByDatetimeAscIdAsc(Long userId, OrderStatus status);
}

