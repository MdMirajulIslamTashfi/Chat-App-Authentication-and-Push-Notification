package com.example.chatApp.exceptionHandler;

public class WeatherServerException extends RuntimeException {
    public WeatherServerException(String message) {
        super("Weather service error: " + message);
    }
}

