package com.votewithblock.gateway.service;

import com.votewithblock.gateway.dto.MonitoringDashboardDto;
import com.votewithblock.gateway.model.ElectionIncident;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardAlertService {

    private final List<Alert> activeAlerts = new CopyOnWriteArrayList<>();
    private final SystemHealthService systemHealthService;

    // Evaluated periodically or triggered by events
    public void evaluateAlerts() {
        activeAlerts.clear();

        MonitoringDashboardDto health = systemHealthService.getSystemHealth();
        
        if (!"HEALTHY".equals(health.getFabricHealth().getStatus())) {
            activeAlerts.add(new Alert("CRITICAL", "Hyperledger Fabric disconnected or degraded"));
        }
        
        if (!"HEALTHY".equals(health.getAiServiceHealth().getStatus())) {
            activeAlerts.add(new Alert("HIGH", "AI Service offline. Biometric verification unavailable."));
        }
        
        if (!"HEALTHY".equals(health.getDatabaseHealth().getStatus())) {
            activeAlerts.add(new Alert("CRITICAL", "Database connection degraded"));
        }
    }

    public void registerIncidentAlert(ElectionIncident incident) {
        if ("CRITICAL".equals(incident.getSeverity()) && "OPEN".equals(incident.getStatus())) {
            activeAlerts.add(new Alert("CRITICAL", "Critical Incident Opened: " + incident.getTitle()));
        }
    }

    public void registerBiometricSpikeAlert() {
        activeAlerts.add(new Alert("HIGH", "Biometric failures spiking above threshold"));
    }

    public List<Alert> getActiveAlerts() {
        return new ArrayList<>(activeAlerts);
    }

    public record Alert(String severity, String message) {}
}
