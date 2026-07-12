package com.votewithblock.gateway.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.votewithblock.gateway.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardCacheService {

    // Cache Public metrics for 30 seconds
    private final Cache<String, PublicDashboardDto> publicMetricsCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(100)
            .build();

    // Cache Admin metrics for 10 seconds
    private final Cache<String, AdminDashboardDto> adminMetricsCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .maximumSize(10)
            .build();

    // Cache Health metrics for 5 seconds
    private final Cache<String, MonitoringDashboardDto> healthMetricsCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .maximumSize(10)
            .build();

    public PublicDashboardDto getPublicDashboardMetrics(String electionCode, Supplier<PublicDashboardDto> fetcher) {
        return publicMetricsCache.get(electionCode, k -> fetcher.get());
    }

    public AdminDashboardDto getAdminDashboardMetrics(String electionCode, Supplier<AdminDashboardDto> fetcher) {
        return adminMetricsCache.get(electionCode, k -> fetcher.get());
    }

    public MonitoringDashboardDto getMonitoringDashboardMetrics(Supplier<MonitoringDashboardDto> fetcher) {
        return healthMetricsCache.get("global", k -> fetcher.get());
    }
    
    public void invalidateAdminCache(String electionCode) {
        adminMetricsCache.invalidate(electionCode);
    }
}
