package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.Election;
import com.votewithblock.gateway.model.ElectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ElectionRepository extends JpaRepository<Election, UUID> {
    Optional<Election> findByElectionCode(String electionCode);
    List<Election> findByStatus(ElectionStatus status);
}
