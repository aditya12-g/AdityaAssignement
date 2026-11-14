package com.example.taskapp.auth;

import com.example.taskapp.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 8, max = 50) String username,
            @NotBlank @Email @Pattern(regexp = ".*_.*", message = "Email must contain an underscore")
            String email,
            @NotBlank
            @Size(min = 8, max = 12)
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                    message = "Password must include uppercase, lowercase, number, and special character")
            String password,
            @NotBlank @Pattern(regexp = "^(Standard User|Administrator)$",
                    message = "User type must be Standard User or Administrator")
            String userType
    ) {
        public UserRole role() {
            return UserRole.fromLabel(userType);
        }
    }

    public record VerifyOtpRequest(@NotBlank @Email String email, @NotBlank @Size(min = 6, max = 6) String otp) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record AuthResponse(String token, String tokenType, String username, String role, String message) {
    }

    public record MessageResponse(String message) {
    }

    public record OtpResponse(String message, String developmentOtp) {
    }
}
