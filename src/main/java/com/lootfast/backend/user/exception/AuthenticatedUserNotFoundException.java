package com.lootfast.backend.user.exception;

public class AuthenticatedUserNotFoundException extends RuntimeException {

    public AuthenticatedUserNotFoundException(String message) {
        super(message);
    }
}
