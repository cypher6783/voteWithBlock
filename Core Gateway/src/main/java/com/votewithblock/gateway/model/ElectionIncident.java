package com.votewithblock.gateway.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "election_incidents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectionIncident {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private UUID electionId;
    
    @Enumerated(EnumType.STRING)
    private IncidentSeverity severity;
    
    @Enumerated(EnumType.STRING)
    private IncidentStatus status;
    
    private String title;
    private String description;
    
    private String createdBy;
    private String assignedTo;
    
    private OffsetDateTime createdAt;
    private OffsetDateTime acknowledgedAt;
    private OffsetDateTime investigatingAt;
    private OffsetDateTime resolvedAt;
    
    private String priority;
    private String comments;
}
