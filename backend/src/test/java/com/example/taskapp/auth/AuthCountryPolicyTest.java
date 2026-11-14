package com.example.taskapp.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.taskapp.common.ApiException;
import com.example.taskapp.geo.CountryLookupService;
import com.example.taskapp.geo.GeoLocationException;
import com.example.taskapp.user.UserStore;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthCountryPolicyTest {
    private final CountryLookupService countryLookup = mock(CountryLookupService.class);
    private final OtpDeliveryService delivery = mock(OtpDeliveryService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthService service = new AuthService(
            new UserStore(), passwordEncoder, mock(JwtService.class), countryLookup,
            delivery, Duration.ofMinutes(5), true, false, "SY,AF,IR");
    private final AuthDtos.RegisterRequest request = new AuthDtos.RegisterRequest(
            "countryuser1", "country_user@example.com", "Strong!123", "Standard User");

    @Test
    void blockedCountryCannotRegisterOrReceiveOtp() {
        when(countryLookup.countryFor("203.0.113.1")).thenReturn("SY");

        ApiException error = assertThrows(ApiException.class,
                () -> service.register(request, "203.0.113.1"));

        assertEquals(HttpStatus.FORBIDDEN, error.status());
        verifyNoInteractions(delivery, passwordEncoder);
    }

    @Test
    void countryProviderFailureDoesNotPermitRegistration() {
        when(countryLookup.countryFor("203.0.113.1"))
                .thenThrow(new GeoLocationException("Country lookup is temporarily unavailable"));

        assertThrows(GeoLocationException.class,
                () -> service.register(request, "203.0.113.1"));

        verifyNoInteractions(delivery, passwordEncoder);
    }

    @Test
    void publicAdministratorRegistrationIsRejectedWhenDisabled() {
        when(countryLookup.countryFor("203.0.113.1")).thenReturn("US");
        var administrator = new AuthDtos.RegisterRequest(
                "adminpolicy1", "admin_policy@example.com", "Strong!123", "Administrator");

        ApiException error = assertThrows(ApiException.class,
                () -> service.register(administrator, "203.0.113.1"));

        assertEquals(HttpStatus.FORBIDDEN, error.status());
        verifyNoInteractions(delivery, passwordEncoder);
    }
}
