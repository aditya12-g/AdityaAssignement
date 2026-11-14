package com.example.taskapp.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.taskapp.user.UserAccount;
import com.example.taskapp.user.UserRole;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtServiceTest {
    private static final String SECRET = "a-test-secret-that-is-long-enough-for-hmac-256";

    @Test
    void createsValidTokenAndRevokesItOnLogout() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        JwtService jwtService = new JwtService(SECRET, Duration.ofMinutes(1),
                environment);
        UserAccount user = new UserAccount(
                "northstar1", "north_star@example.com", "hash", UserRole.STANDARD_USER);

        String token = jwtService.createToken(user);

        assertTrue(jwtService.isValid(token));
        assertEquals(user.email(), jwtService.parse(token).getSubject());
        jwtService.revoke(token);
        assertFalse(jwtService.isValid(token));
    }

    @Test
    void rejectsExpiredToken() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        JwtService jwtService = new JwtService(SECRET, Duration.ofSeconds(-1),
                environment);
        UserAccount user = new UserAccount(
                "northstar1", "north_star@example.com", "hash", UserRole.STANDARD_USER);

        assertFalse(jwtService.isValid(jwtService.createToken(user)));
    }
}
