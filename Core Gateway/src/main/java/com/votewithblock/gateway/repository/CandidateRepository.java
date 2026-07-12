package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, UUID> {
    List<Candidate> findByElectionIdOrderByDisplayOrderAsc(UUID electionId);
    List<Candidate> findByElectionIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID electionId);
}
