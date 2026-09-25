package com.astropaper.api.articles;

public class ArticleConflictException extends RuntimeException {

    public ArticleConflictException(String message) {
        super(message);
    }
}
