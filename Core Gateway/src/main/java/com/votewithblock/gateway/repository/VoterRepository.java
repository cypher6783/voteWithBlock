package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.VoterAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoterRepository extends JpaRepository<VoterAccount, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VoterAccount> findByNationalId(String nationalId);
}
