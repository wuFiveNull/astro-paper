package com.astropaper.api.auth;

public class TooManyLoginAttemptsException extends RuntimeException {

    public TooManyLoginAttemptsException() {
        super("Too many failed sign-in attempts. Try again later.");
    }
}
