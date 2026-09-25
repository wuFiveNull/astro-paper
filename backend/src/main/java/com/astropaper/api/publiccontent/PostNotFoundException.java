package com.astropaper.api.publiccontent;

public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException(String slug) {
        super("Post not found: " + slug);
    }
}
