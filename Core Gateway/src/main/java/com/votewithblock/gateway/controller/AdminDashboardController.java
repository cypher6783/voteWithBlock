package com.votewithblock.gateway.controller;

import com.votewithblock.gateway.dto.AdminDashboardDto;
import com.votewithblock.gateway.dto.MonitoringDashboardDto;
import com.votewithblock.gateway.model.Election;
import com.votewithblock.gateway.service.DashboardCacheService;
import com.votewithblock.gateway.service.DashboardMetricsService;
import com.votewithblock.gateway.service.ElectionMetricsService;
import com.votewithblock.gateway.service.ElectionService;
import com.votewithblock.gateway.service.SystemHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final ElectionService electionService;
    private final ElectionMetricsService electionMetricsService;
    private final DashboardMetricsService dashboardMetricsService;
    private final SystemHealthService systemHealthService;
    private final DashboardCacheService dashboardCacheService;

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDto> getAdminDashboard() {
        // We only require either ELECTION_ADMIN or SUPER_ADMIN
        // This is handled by a filter or method security config elsewhere
        Election active = electionService.getActiveElection();
        if (active == null) {
            return ResponseEntity.noContent().build();
        }

        AdminDashboardDto metrics = dashboardCacheService.getAdminDashboardMetrics(
                active.getElectionCode(),
                () -> AdminDashboardDto.builder()
                        .electionMetrics(electionMetricsService.getPublicMetrics(active.getElectionCode()))
                        .biometricMetrics(dashboardMetricsService.getBiometricMetrics())
                        .securityMetrics(dashboardMetricsService.getSecurityMetrics())
                        .operationalMetrics(systemHealthService.getSystemHealth())
                        .build()
        );

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/metrics/operational")
    public ResponseEntity<MonitoringDashboardDto> getOperationalMetrics() {
        MonitoringDashboardDto metrics = dashboardCacheService.getMonitoringDashboardMetrics(
                () -> systemHealthService.getSystemHealth()
        );
        return ResponseEntity.ok(metrics);
    }
}
