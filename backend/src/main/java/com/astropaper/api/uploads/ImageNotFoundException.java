package com.astropaper.api.uploads;

public class ImageNotFoundException extends RuntimeException {

    public ImageNotFoundException() {
        super("The requested image does not exist.");
    }
}
