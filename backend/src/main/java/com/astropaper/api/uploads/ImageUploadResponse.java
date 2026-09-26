package com.astropaper.api.uploads;

public record ImageUploadResponse(String url, String filename, String contentType, long size) {
}
