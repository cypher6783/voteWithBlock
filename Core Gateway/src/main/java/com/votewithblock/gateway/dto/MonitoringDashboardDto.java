package com.votewithblock.gateway.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class MonitoringDashboardDto {
    private String overallStatus; // HEALTHY, DEGRADED, CRITICAL
    private ComponentHealth gatewayHealth;
    private ComponentHealth aiServiceHealth;
    private ComponentHealth databaseHealth;
    private ComponentHealth fabricHealth;
    private BlockchainMetrics blockchainMetrics;
    
    @Data
    @Builder
    public static class ComponentHealth {
        private String status;
        private String details;
    }

    @Data
    @Builder
    public static class BlockchainMetrics {
        private String lastSuccessfulTransaction;
        private String lastFailedTransaction;
        private double averageSubmissionLatencyMs;
        private double averageQueryLatencyMs;
    }
}
