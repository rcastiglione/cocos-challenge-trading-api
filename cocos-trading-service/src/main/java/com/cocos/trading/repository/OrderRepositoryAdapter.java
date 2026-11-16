package com.cocos.trading.repository;

import com.cocos.trading.application.port.out.OrderRepositoryPort;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.jpa.SpringDataOrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Outbound persistence adapter implementing {@link OrderRepositoryPort}.
 */
@Component
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final SpringDataOrderRepository springDataOrderRepository;

    @Override
    public OrderJpaEntity save(OrderJpaEntity order) {
        return springDataOrderRepository.save(order);
    }

    @Override
    public Optional<OrderJpaEntity> findById(Long id) {
        return springDataOrderRepository.findById(id);
    }

    @Override
    public Optional<OrderJpaEntity> findByIdempotencyKey(UUID idempotencyKey) {
        return springDataOrderRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public List<OrderJpaEntity> findByUserIdAndStatus(Long userId, OrderStatus status) {
        return springDataOrderRepository.findByUserIdAndStatusOrderByDatetimeAscIdAsc(userId, status);
    }
}
