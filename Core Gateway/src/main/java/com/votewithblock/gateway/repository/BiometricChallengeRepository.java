package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.BiometricChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BiometricChallengeRepository extends JpaRepository<BiometricChallenge, Long> {
    Optional<BiometricChallenge> findByChallengeIdAndNationalId(UUID challengeId, String nationalId);
}
