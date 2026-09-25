package com.astropaper.api.auth;

public class AdminResourceNotFoundException extends RuntimeException {

    public AdminResourceNotFoundException(String message) {
        super(message);
    }
}
