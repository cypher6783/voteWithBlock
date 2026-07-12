package com.votewithblock.gateway.controller;

import com.votewithblock.gateway.dto.PublicDashboardDto;
import com.votewithblock.gateway.model.Election;
import com.votewithblock.gateway.service.DashboardCacheService;
import com.votewithblock.gateway.service.ElectionMetricsService;
import com.votewithblock.gateway.service.ElectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicDashboardController {

    private final ElectionService electionService;
    private final ElectionMetricsService electionMetricsService;
    private final DashboardCacheService dashboardCacheService;

    @GetMapping("/dashboard")
    public ResponseEntity<PublicDashboardDto> getDashboardMetrics() {
        Election active = electionService.getActiveElection();
        if (active == null) {
            return ResponseEntity.noContent().build();
        }

        PublicDashboardDto metrics = dashboardCacheService.getPublicDashboardMetrics(
                active.getElectionCode(),
                () -> electionMetricsService.getPublicMetrics(active.getElectionCode())
        );

        return ResponseEntity.ok(metrics);
    }
}
