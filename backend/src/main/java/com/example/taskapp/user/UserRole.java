package com.example.taskapp.user;

public enum UserRole {
    STANDARD_USER("Standard User"),
    ADMINISTRATOR("Administrator");

    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static UserRole fromLabel(String label) {
        for (UserRole role : values()) {
            if (role.label.equals(label)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unsupported user type");
    }
}
