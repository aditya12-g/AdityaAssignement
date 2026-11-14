package com.example.taskapp.auth;

import com.example.taskapp.user.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final Duration expiration;
    private final ConcurrentMap<String, Instant> revokedTokens = new ConcurrentHashMap<>();

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") Duration expiration,
            Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of("dev"))
                && (secret.startsWith("dev-only-") || secret.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8).length < 32)) {
            throw new IllegalStateException("Set a strong JWT_SECRET before starting outside the dev profile");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String createToken(UserAccount user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.email())
                .claim("username", user.username())
                .claim("role", user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(token).getPayload();
    }

    public boolean isValid(String token) {
        try {
            Claims claims = parse(token);
            return claims.getSubject() != null
                    && claims.getId() != null
                    && !isRevoked(claims.getId());
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public void revoke(String token) {
        Claims claims = parse(token);
        Instant now = Instant.now();
        revokedTokens.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        revokedTokens.put(claims.getId(), claims.getExpiration().toInstant());
    }

    private boolean isRevoked(String tokenId) {
        Instant expiry = revokedTokens.get(tokenId);
        if (expiry == null) {
            return false;
        }
        if (!expiry.isAfter(Instant.now())) {
            revokedTokens.remove(tokenId, expiry);
            return false;
        }
        return true;
    }
}
