package com.example.taskapp.auth;

import com.example.taskapp.user.UserAccount;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
    @GetMapping("/api/dashboard")
    public Map<String, String> dashboard(@AuthenticationPrincipal UserAccount account) {
        return Map.of("message", "Welcome " + account.username());
    }

    @GetMapping("/api/settings")
    public Map<String, String> settings() {
        return Map.of("message", "Administrator settings");
    }
}
