package com.samuel.app.exceptions;

public class ProfileAlreadyExistsException extends RuntimeException {
    public ProfileAlreadyExistsException(String message) {
        super(message);
    }
}