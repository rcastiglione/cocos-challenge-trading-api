package com.cocos.trading.repository.jpa;

import com.cocos.trading.repository.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link UserJpaEntity}.
 */
@Repository
public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, Long> {
}

