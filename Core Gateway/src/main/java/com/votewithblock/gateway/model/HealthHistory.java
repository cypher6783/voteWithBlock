package com.votewithblock.gateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "health_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String component;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "jsonb")
    private String metrics;

    @Column(nullable = false)
    private OffsetDateTime recordedAt;
}
