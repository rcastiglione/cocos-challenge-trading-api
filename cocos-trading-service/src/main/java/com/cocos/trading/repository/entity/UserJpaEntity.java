package com.cocos.trading.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity mapped to the {@code users} table.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Column(name = "email", nullable = false)
    private String email;

    // TODO: The underlying DB schema currently allows NULLs for this column. Pending a Flyway/Liquibase migration to enforce NOT NULL at the database level.
    @Column(name = "accountnumber", nullable = false)
    private String accountNumber;
}
