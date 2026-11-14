package com.example.taskapp.auth;

import com.example.taskapp.common.ApiException;
import com.example.taskapp.geo.CountryLookupService;
import com.example.taskapp.user.UserAccount;
import com.example.taskapp.user.UserRole;
import com.example.taskapp.user.UserStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserStore userStore;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CountryLookupService countryLookupService;
    private final OtpDeliveryService otpDeliveryService;
    private final Duration otpExpiration;
    private final boolean includeOtpInResponse;
    private final boolean allowPublicAdminRegistration;
    private final Map<String, PendingRegistration> pendingRegistrations = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final java.util.Set<String> blockedCountries;

    public AuthService(
            UserStore userStore,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CountryLookupService countryLookupService,
            OtpDeliveryService otpDeliveryService,
            @Value("${app.otp.expiration}") Duration otpExpiration,
            @Value("${app.otp.include-in-response}") boolean includeOtpInResponse,
            @Value("${app.auth.allow-public-admin-registration}") boolean allowPublicAdminRegistration,
            @Value("${app.geo.blocked-countries}") String blockedCountries) {
        this.userStore = userStore;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.countryLookupService = countryLookupService;
        this.otpDeliveryService = otpDeliveryService;
        this.otpExpiration = otpExpiration;
        this.includeOtpInResponse = includeOtpInResponse;
        this.allowPublicAdminRegistration = allowPublicAdminRegistration;
        this.blockedCountries = Arrays.stream(blockedCountries.split(","))
                .map(country -> country.trim().toUpperCase(Locale.ROOT))
                .filter(country -> !country.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public AuthDtos.OtpResponse register(
            AuthDtos.RegisterRequest request,
            String remoteAddress) {
        String country = countryLookupService.countryFor(remoteAddress);
        if (blockedCountries.contains(country)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Sign-up is not available in your country");
        }
        UserRole requestedRole = request.role();
        if (requestedRole == UserRole.ADMINISTRATOR && !allowPublicAdminRegistration) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Administrator accounts must be provisioned by an administrator");
        }

        synchronized (this) {
            Instant now = Instant.now();
            pendingRegistrations.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
            if (userStore.findByEmail(request.email()).isPresent()
                    || userStore.findByUsername(request.username()).isPresent()
                    || pendingRegistrations.values().stream()
                            .anyMatch(pending -> pending.account().username()
                                    .equalsIgnoreCase(request.username()))) {
                throw new ApiException(HttpStatus.CONFLICT, "Email or username is already registered");
            }
            String normalizedEmail = normalizeEmail(request.email());
            PendingRegistration existingPending = pendingRegistrations.get(normalizedEmail);
            if (existingPending != null) {
                throw new ApiException(HttpStatus.CONFLICT, "A verification code has already been sent");
            }

            String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
            UserAccount account = new UserAccount(
                    request.username(),
                    request.email(),
                    passwordEncoder.encode(request.password()),
                    requestedRole);
            try {
                otpDeliveryService.deliver(account.email(), otp);
            } catch (org.springframework.mail.MailException exception) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Unable to deliver the verification code");
            }
            pendingRegistrations.put(normalizedEmail,
                    new PendingRegistration(account, otp, Instant.now().plus(otpExpiration), 0));
            return new AuthDtos.OtpResponse(
                    "Verification code sent",
                    includeOtpInResponse ? otp : null);
        }
    }

    public AuthDtos.MessageResponse verifyOtp(AuthDtos.VerifyOtpRequest request) {
        synchronized (this) {
            String normalizedEmail = normalizeEmail(request.email());
            PendingRegistration pending = pendingRegistrations.get(normalizedEmail);
            if (pending == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code");
            }
            if (!pending.expiresAt().isAfter(Instant.now())) {
                pendingRegistrations.remove(normalizedEmail);
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code");
            }
            if (!java.security.MessageDigest.isEqual(
                    pending.otp().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    request.otp().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                if (pending.failedAttempts() + 1 >= 5) {
                    pendingRegistrations.remove(normalizedEmail);
                } else {
                    pendingRegistrations.put(normalizedEmail,
                            new PendingRegistration(
                                    pending.account(),
                                    pending.otp(),
                                    pending.expiresAt(),
                                    pending.failedAttempts() + 1));
                }
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code");
            }
            userStore.save(pending.account());
            pendingRegistrations.remove(normalizedEmail);
            return new AuthDtos.MessageResponse("Account verified. You can now log in.");
        }
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        UserAccount account = userStore.findByEmail(request.email()).orElse(null);
        if (account == null || !passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Login Failed");
        }
        return new AuthDtos.AuthResponse(
                jwtService.createToken(account),
                "Bearer",
                account.username(),
                account.role().label(),
                "Login successful");
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    private record PendingRegistration(UserAccount account, String otp, Instant expiresAt, int failedAttempts) {
    }
}
