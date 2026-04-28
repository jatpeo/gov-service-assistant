package com.gov.assistant.repository.auth;

import com.gov.assistant.entity.auth.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRoleAndStatus(UserAccount.UserRole role, UserAccount.Status status);

    List<UserAccount> findAllByOrderByCreatedAtDesc();
}
