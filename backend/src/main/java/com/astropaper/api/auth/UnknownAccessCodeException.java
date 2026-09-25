package com.astropaper.api.auth;

public class UnknownAccessCodeException extends RuntimeException {

    public UnknownAccessCodeException(String message) {
        super(message);
    }
}
