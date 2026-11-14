package com.example.taskapp.geo;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GeoLocationExceptionHandler {
    @ExceptionHandler(GeoLocationException.class)
    public ResponseEntity<Map<String, String>> handleGeoLocation(GeoLocationException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", exception.getMessage()));
    }
}
