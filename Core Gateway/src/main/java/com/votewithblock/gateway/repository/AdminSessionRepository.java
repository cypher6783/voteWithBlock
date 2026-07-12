package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.AdminSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminSessionRepository extends JpaRepository<AdminSession, String> {
    Optional<AdminSession> findByJti(String jti);
    boolean existsByJtiAndRevokedFalse(String jti);
}
