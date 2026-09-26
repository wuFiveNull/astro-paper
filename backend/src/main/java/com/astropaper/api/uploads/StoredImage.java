package com.astropaper.api.uploads;

import java.nio.file.Path;

public record StoredImage(Path path, String filename, String contentType, long size) {
}
