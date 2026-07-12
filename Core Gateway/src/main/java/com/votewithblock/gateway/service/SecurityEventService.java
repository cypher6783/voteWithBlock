package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.SecurityEvent;
import com.votewithblock.gateway.model.SecurityEventType;
import com.votewithblock.gateway.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SecurityEventService {

    private final SecurityEventRepository securityEventRepository;

    @Transactional
    public SecurityEvent recordEvent(SecurityEventType eventType, String severity, String source,
                                     String ipHash, String userHash, String details) {
        SecurityEvent event = SecurityEvent.builder()
                .eventType(eventType)
                .severity(severity)
                .source(source)
                .ipHash(ipHash)
                .userHash(userHash)
                .details(details)
                .occurredAt(OffsetDateTime.now())
                .build();
        log.info("Security event recorded: {} - {} - {}", eventType, source, severity);
        return securityEventRepository.save(event);
    }

    public Page<SecurityEvent> getEvents(SecurityEventType eventType, int page, int size) {
        return securityEventRepository.findByEventType(eventType, PageRequest.of(page, size));
    }

    public Page<SecurityEvent> getEventsInRange(OffsetDateTime start, OffsetDateTime end, int page, int size) {
        return securityEventRepository.findByOccurredAtBetween(start, end, PageRequest.of(page, size));
    }

    public Page<SecurityEvent> getEventsByTypeAndRange(SecurityEventType eventType, 
                                                        OffsetDateTime start, OffsetDateTime end, 
                                                        int page, int size) {
        return securityEventRepository.findByEventTypeAndOccurredAtBetween(eventType, start, end, PageRequest.of(page, size));
    }

    public Map<String, Long> getEventStats() {
        long last24h = securityEventRepository.countByOccurredAtAfter(OffsetDateTime.now().minusHours(24));
        long last7d = securityEventRepository.countByOccurredAtAfter(OffsetDateTime.now().minusDays(7));
        
        return Map.of(
            "total24h", last24h,
            "total7d", last7d,
            "failedLogin", securityEventRepository.countByEventType(SecurityEventType.FAILED_LOGIN),
            "failedAdminLogin", securityEventRepository.countByEventType(SecurityEventType.FAILED_ADMIN_LOGIN),
            "rateLimit", securityEventRepository.countByEventType(SecurityEventType.RATE_LIMIT),
            "accountLockout", securityEventRepository.countByEventType(SecurityEventType.ACCOUNT_LOCKOUT),
            "biometricFailure", securityEventRepository.countByEventType(SecurityEventType.BIOMETRIC_FAILURE),
            "suspiciousActivity", securityEventRepository.countByEventType(SecurityEventType.SUSPICIOUS_ACTIVITY)
        );
    }
}
