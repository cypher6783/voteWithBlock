package com.votewithblock.gateway.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "elections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Election {
    
    @Id
    private UUID id;
    
    private String electionCode;
    private String title;
    private String description;
    
    @Enumerated(EnumType.STRING)
    private ElectionType type;
    
    @Enumerated(EnumType.STRING)
    private ElectionStatus status;
    
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    
    @Version
    private Integer version;
    
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
}
