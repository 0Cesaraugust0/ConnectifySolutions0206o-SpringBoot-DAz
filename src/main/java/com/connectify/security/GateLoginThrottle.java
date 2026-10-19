package com.connectify.security;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Bounds failed gate-password attempts without storing passwords or event codes. */
@Component
public class GateLoginThrottle {
    private static final int MAX_FAILURES = 10;
    private static final int MAX_ADDRESSES = 10000;
    private final ConcurrentHashMap<String, Window> failures = new ConcurrentHashMap<>();

    public boolean allows(String address) {
        Instant now = Instant.now();
        failures.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        Window window = failures.get(address);
        return window == null ? failures.size() < MAX_ADDRESSES : window.count() < MAX_FAILURES;
    }
    public void failed(String address) {
        Instant now = Instant.now();
        if (failures.size() < MAX_ADDRESSES || failures.containsKey(address))
            failures.compute(address, (key, old) -> old == null || old.expiresAt().isBefore(now)
                    ? new Window(1, now.plusSeconds(900)) : new Window(old.count() + 1, old.expiresAt()));
    }
    public void succeeded(String address) { failures.remove(address); }
    private record Window(int count, Instant expiresAt) { }
}
