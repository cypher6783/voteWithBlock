package com.votewithblock.gateway.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "incident_assignment_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentAssignmentHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long incidentId;
    private String assignedFrom;
    private String assignedTo;
    private String assignedBy;
    private OffsetDateTime assignedAt;
    private String reason;
}
