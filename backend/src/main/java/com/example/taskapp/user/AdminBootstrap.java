package com.example.taskapp.user;

import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev")
public class AdminBootstrap implements ApplicationRunner {
    private static final Pattern ADMIN_PASSWORD =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,12}$");
    private static final Pattern ADMIN_EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+_[A-Za-z0-9._%+-]*@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserStore userStore;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String username;
    private final String password;

    public AdminBootstrap(
            UserStore userStore,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.bootstrap-admin.email}") String email,
            @Value("${app.auth.bootstrap-admin.username}") String username,
            @Value("${app.auth.bootstrap-admin.password}") String password) {
        this.userStore = userStore;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.length() < 8 || username.length() > 50
                || !ADMIN_EMAIL.matcher(email).matches()
                || !ADMIN_PASSWORD.matcher(password).matches()) {
            throw new IllegalStateException("Configured administrator credentials do not meet account requirements");
        }
        userStore.save(new UserAccount(
                username,
                email,
                passwordEncoder.encode(password),
                UserRole.ADMINISTRATOR));
    }
}
