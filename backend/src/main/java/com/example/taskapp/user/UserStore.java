package com.example.taskapp.user;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class UserStore {
    private final ConcurrentMap<String, UserAccount> usersByEmail = new ConcurrentHashMap<>();

    public Optional<UserAccount> findByEmail(String email) {
        return Optional.ofNullable(usersByEmail.get(normalize(email)));
    }

    public Optional<UserAccount> findByUsername(String username) {
        return usersByEmail.values().stream()
                .filter(user -> user.username().equalsIgnoreCase(username))
                .findFirst();
    }

    public void save(UserAccount account) {
        usersByEmail.put(normalize(account.email()), account);
    }

    private String normalize(String email) {
        return email.toLowerCase(java.util.Locale.ROOT);
    }
}
