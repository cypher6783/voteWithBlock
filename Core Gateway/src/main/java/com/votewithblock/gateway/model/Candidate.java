package com.votewithblock.gateway.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "candidates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidate {
    
    @Id
    private UUID id;
    
    private UUID electionId;
    
    private String name;
    private String party;
    private String manifesto;
    private String imageUrl;
    
    private Integer displayOrder;
    private boolean isActive;
    
    @Version
    private Integer version;
    
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
