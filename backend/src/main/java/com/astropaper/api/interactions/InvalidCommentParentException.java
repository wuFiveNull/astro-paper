package com.astropaper.api.interactions;

public class InvalidCommentParentException extends RuntimeException {

    public InvalidCommentParentException() {
        super("A reply must reference a published comment on the same article.");
    }
}
