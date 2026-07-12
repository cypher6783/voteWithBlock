package com.votewithblock.gateway.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class SecurityDashboardDto {
    private long loginFailures;
    private long adminLoginFailures;
    private long rateLimitEvents;
    private long suspiciousActivities;
}
