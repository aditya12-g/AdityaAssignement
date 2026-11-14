package com.example.taskapp.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevOtpDeliveryService implements OtpDeliveryService {
    @Override
    public void deliver(String email, String otp) {
        // The development UI returns the code directly; do not send or log it.
    }
}
