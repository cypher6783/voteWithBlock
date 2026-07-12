package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.VoterBiometric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoterBiometricRepository extends JpaRepository<VoterBiometric, Long> {
    Optional<VoterBiometric> findByNationalId(String nationalId);
}
