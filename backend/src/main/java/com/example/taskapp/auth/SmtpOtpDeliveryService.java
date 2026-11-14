package com.example.taskapp.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev")
public class SmtpOtpDeliveryService implements OtpDeliveryService {
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpOtpDeliveryService(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void deliver(String email, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Your verification code");
        message.setText("Your verification code is " + otp + ". It expires in five minutes.");
        mailSender.send(message);
    }
}
