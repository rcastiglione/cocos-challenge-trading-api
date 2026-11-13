package com.cocos.trading.repository;

import com.cocos.trading.application.port.out.OrderRepositoryPort;
import com.cocos.trading.domain.model.Order;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.jpa.SpringDataOrderRepository;
import com.cocos.trading.repository.mapper.PersistenceMapper;
import java.util.List;
import java.util.Optional;
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
    public Order save(Order order) {
        OrderJpaEntity saved = springDataOrderRepository.save(PersistenceMapper.toEntity(order));
        return PersistenceMapper.toDomain(saved);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return springDataOrderRepository.findById(id)
                .map(PersistenceMapper::toDomain);
    }

    @Override
    public List<Order> findByUserIdAndStatus(Long userId, OrderStatus status) {
        return springDataOrderRepository.findByUserIdAndStatusOrderByDatetimeAscIdAsc(userId, status).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }
}
