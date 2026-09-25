package com.astropaper.api.articles;

public class ArticleOwnershipException extends RuntimeException {

    public ArticleOwnershipException() {
        super("You can only manage articles assigned to your account.");
    }
}
