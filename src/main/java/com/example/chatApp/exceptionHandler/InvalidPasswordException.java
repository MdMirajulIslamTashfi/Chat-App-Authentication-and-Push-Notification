package com.example.chatApp.exceptionHandler;

public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException(String s) {
        super("The password you entered is incorrect");
    }
}