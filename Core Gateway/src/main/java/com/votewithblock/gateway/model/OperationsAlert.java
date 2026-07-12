package com.votewithblock.gateway.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "operations_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationsAlert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;
    
    @Enumerated(EnumType.STRING)
    private AlertStatus status;
    
    private String source;
    private String message;
    
    @ManyToOne
    @JoinColumn(name = "parent_alert_id")
    private OperationsAlert parentAlert;
    
    private String acknowledgedBy;
    private OffsetDateTime acknowledgedAt;
    
    private String resolvedBy;
    private OffsetDateTime resolvedAt;
    
    private OffsetDateTime createdAt;
    private String metadata;
}
