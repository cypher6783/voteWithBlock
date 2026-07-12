package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.VoteRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoteRequestLogRepository extends JpaRepository<VoteRequestLog, Long> {

    Optional<VoteRequestLog> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
