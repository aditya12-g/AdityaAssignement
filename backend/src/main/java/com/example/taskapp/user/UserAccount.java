package com.example.taskapp.user;

public record UserAccount(
        String username,
        String email,
        String passwordHash,
        UserRole role
) {
}
