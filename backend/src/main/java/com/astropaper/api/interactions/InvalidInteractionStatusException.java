package com.astropaper.api.interactions;

public class InvalidInteractionStatusException extends RuntimeException {

    public InvalidInteractionStatusException(String message) {
        super(message);
    }
}
