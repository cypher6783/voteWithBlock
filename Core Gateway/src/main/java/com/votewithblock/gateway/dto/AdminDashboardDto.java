package com.votewithblock.gateway.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class AdminDashboardDto {
    private PublicDashboardDto electionMetrics;
    private BiometricMetricsDto biometricMetrics;
    private SecurityDashboardDto securityMetrics;
    private MonitoringDashboardDto operationalMetrics;
}
