package com.cocos.trading.repository;

import com.cocos.trading.application.port.out.UserRepositoryPort;
import com.cocos.trading.repository.entity.UserJpaEntity;
import com.cocos.trading.repository.jpa.SpringDataUserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Outbound persistence adapter implementing {@link UserRepositoryPort}.
 */
@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final SpringDataUserRepository springDataUserRepository;

    @Override
    public Optional<UserJpaEntity> findById(Long id) {
        return springDataUserRepository.findById(id);
    }
}
