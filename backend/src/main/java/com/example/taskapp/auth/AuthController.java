package com.example.taskapp.auth;

import com.example.taskapp.user.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.OtpResponse> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request,
            HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(authService.register(request, servletRequest.getRemoteAddr()));
    }

    @PostMapping("/verify-otp")
    public AuthDtos.MessageResponse verifyOtp(@Valid @RequestBody AuthDtos.VerifyOtpRequest request) {
        return authService.verifyOtp(request);
    }

    @GetMapping("/me")
    public Map<String, String> currentUser(@AuthenticationPrincipal UserAccount account) {
        return Map.of("username", account.username(), "role", account.role().label());
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public AuthDtos.MessageResponse logout(
            @AuthenticationPrincipal UserAccount account,
            HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        jwtService.revoke(authorization.substring("Bearer ".length()));
        return new AuthDtos.MessageResponse(account.username() + " Logged Out");
    }

    @GetMapping("/error")
    public ResponseEntity<Map<String, String>> authenticationError() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Login Failed"));
    }
}
