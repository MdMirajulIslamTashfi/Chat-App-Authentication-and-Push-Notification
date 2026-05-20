package com.example.chatApp.exceptionHandler;

public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
