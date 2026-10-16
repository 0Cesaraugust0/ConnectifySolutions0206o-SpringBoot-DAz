package com.connectify.repository;

import com.connectify.entity.Role;
import com.connectify.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findLockedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where lower(u.email) = lower(:email)")
    Optional<UserAccount> findLockedByEmail(@Param("email") String email);

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);
}
