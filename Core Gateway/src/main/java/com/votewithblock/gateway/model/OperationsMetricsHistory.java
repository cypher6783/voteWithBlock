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
import java.util.UUID;

@Entity
@Table(name = "operations_metrics_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationsMetricsHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String metricType;
    private String metricName;
    private Long metricValue;
    private UUID electionId;
    private OffsetDateTime recordedAt;
}
