package com.astropaper.api.auth;

public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException() {
        super("This account is not available.");
    }
}
