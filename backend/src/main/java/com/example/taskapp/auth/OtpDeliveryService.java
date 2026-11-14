package com.example.taskapp.auth;

public interface OtpDeliveryService {
    void deliver(String email, String otp);
}
