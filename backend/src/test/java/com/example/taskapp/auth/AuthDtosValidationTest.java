package com.example.taskapp.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class AuthDtosValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsSignupValuesMatchingTheMachineTest() {
        var request = new AuthDtos.RegisterRequest(
                "northstar1", "north_star@example.com", "Strong!123", "Administrator");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsPasswordMissingRequiredCharacterClasses() {
        var request = new AuthDtos.RegisterRequest(
                "northstar1", "north_star@example.com", "alllower1!", "Standard User");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void requiresAnUnderscoreInEmail() {
        var request = new AuthDtos.RegisterRequest(
                "northstar1", "northstar@example.com", "Strong!123", "Standard User");

        assertFalse(validator.validate(request).isEmpty());
    }
}
