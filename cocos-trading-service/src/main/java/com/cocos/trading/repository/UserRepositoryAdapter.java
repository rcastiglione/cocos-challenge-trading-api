package com.cocos.trading.repository;

import com.cocos.trading.application.port.out.UserRepositoryPort;
import com.cocos.trading.domain.model.User;
import com.cocos.trading.repository.jpa.SpringDataUserRepository;
import com.cocos.trading.repository.mapper.PersistenceMapper;
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
    public Optional<User> findById(Long id) {
        return springDataUserRepository.findById(id)
                .map(PersistenceMapper::toDomain);
    }
}
