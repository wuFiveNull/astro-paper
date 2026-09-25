package com.astropaper.api.auth;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException() {
        super("An account with that username or email already exists.");
    }
}
