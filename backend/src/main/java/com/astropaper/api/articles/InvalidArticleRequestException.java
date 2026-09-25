package com.astropaper.api.articles;

public class InvalidArticleRequestException extends RuntimeException {

    public InvalidArticleRequestException(String message) {
        super(message);
    }
}
