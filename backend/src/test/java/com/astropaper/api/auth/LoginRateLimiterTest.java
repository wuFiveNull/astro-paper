package com.astropaper.api.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimiterTest {

    @Test
    void blocksRepeatedFailuresForOneAddressAndUsernameThenClearsAfterSuccess() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String key = limiter.key("Writer", "192.0.2.10");

        for (int attempt = 0; attempt < 10; attempt++) {
            assertDoesNotThrow(() -> limiter.assertAllowed(key));
            limiter.recordFailure(key);
        }

        assertThrows(TooManyLoginAttemptsException.class, () -> limiter.assertAllowed(key));
        assertNotEquals(key, limiter.key("reader", "192.0.2.10"));
        assertNotEquals(key, limiter.key("writer", "192.0.2.11"));

        limiter.recordSuccess(key);
        assertDoesNotThrow(() -> limiter.assertAllowed(key));
    }
}
