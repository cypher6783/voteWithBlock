package com.votewithblock.gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();
    private final int maxAttempts;
    private final long windowMillis;

    public RateLimitService(
            @Value("${app.rate-limit.max-attempts:12}") int maxAttempts,
            @Value("${app.rate-limit.window-seconds:60}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000;
    }

    public void requireAllowed(String key) {
        long now = clock.millis();
        Deque<Long> attempts = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && now - attempts.peekFirst() > windowMillis) {
                attempts.removeFirst();
            }
            if (attempts.size() >= maxAttempts) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
            }
            attempts.addLast(now);
        }
    }
}
