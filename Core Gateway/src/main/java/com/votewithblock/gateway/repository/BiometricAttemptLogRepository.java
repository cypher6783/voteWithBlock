package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.BiometricAttemptLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BiometricAttemptLogRepository extends JpaRepository<BiometricAttemptLog, Long> {
}
