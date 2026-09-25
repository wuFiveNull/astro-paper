package com.astropaper.api.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class LoginRateLimiter {

    private static final int MAX_FAILURES = 10;
    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, AttemptWindow> attempts = new LinkedHashMap<>(128, 0.75f, true);

    public String key(String username, String remoteAddress) {
        String normalizedUser = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String normalizedAddress = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.trim();
        return normalizedAddress + "\u0000" + normalizedUser;
    }

    public synchronized void assertAllowed(String key) {
        Instant now = Instant.now();
        resetExpired(key, now);
        AttemptWindow window = attempts.get(key);
        if (window != null && window.failures >= MAX_FAILURES) throw new TooManyLoginAttemptsException();
    }

    public synchronized void recordFailure(String key) {
        Instant now = Instant.now();
        resetExpired(key, now);
        AttemptWindow window = attempts.get(key);
        if (window == null) {
            if (attempts.size() >= MAX_TRACKED_KEYS) {
                Iterator<String> oldest = attempts.keySet().iterator();
                if (oldest.hasNext()) {
                    oldest.next();
                    oldest.remove();
                }
            }
            window = new AttemptWindow(now);
            attempts.put(key, window);
        }
        window.failures++;
    }

    public synchronized void recordSuccess(String key) {
        attempts.remove(key);
    }

    private void resetExpired(String key, Instant now) {
        AttemptWindow window = attempts.get(key);
        if (window != null && !now.isBefore(window.startedAt.plus(WINDOW))) attempts.remove(key);
    }

    private static final class AttemptWindow {
        private final Instant startedAt;
        private int failures;

        private AttemptWindow(Instant startedAt) {
            this.startedAt = startedAt;
        }
    }
}
