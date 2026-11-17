package com.cocos.trading.repository.jpa;

import com.cocos.trading.repository.entity.UserJpaEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link UserJpaEntity}.
 */
@Repository
public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, Long> {

    /**
     * Acquires a row-level pessimistic write lock ({@code SELECT ... FOR UPDATE}) on the user row
     * to serialize concurrent balance-mutating transactions per user.
     *
     * @param id The user ID.
     * @return An {@link Optional} containing the locked {@link UserJpaEntity} if found.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT u FROM UserJpaEntity u WHERE u.id = :id")
    Optional<UserJpaEntity> findAndLockById(@Param("id") Long id);
}

