package com.astropaper.api.uploads;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ImageStorageService {

    private static final int HEADER_BYTES = 32;
    private static final Pattern STORED_FILENAME = Pattern.compile("[a-f0-9-]{36}\\.(?:jpg|png|gif|webp|avif)");
    private static final Map<String, ImageType> TYPES = Map.of(
        "image/jpeg", new ImageType("jpg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}),
        "image/png", new ImageType("png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}),
        "image/gif", new ImageType("gif", new byte[]{0x47, 0x49, 0x46, 0x38}),
        "image/webp", new ImageType("webp", new byte[]{0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50}),
        "image/avif", new ImageType("avif", new byte[]{0, 0, 0, 0, 0x66, 0x74, 0x79, 0x70})
    );

    private final Path root;
    private final long maxBytes;

    public ImageStorageService(
        @Value("${app.uploads.directory:/data/uploads}") String directory,
        @Value("${app.uploads.max-file-size-bytes:10485760}") long maxBytes
    ) {
        if (maxBytes < 1) throw new IllegalArgumentException("Image upload size must be positive.");
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ImageUploadException("Choose an image file to upload.");
        if (file.getSize() > maxBytes) {
            throw new ImageUploadException("The image is larger than the configured upload limit.");
        }

        try {
            Files.createDirectories(root);
            byte[] header = readHeader(file);
            ImageType type = detectType(header);
            String filename = UUID.randomUUID() + "." + type.extension();
            Path target = root.resolve(filename).normalize();
            Path temporary = Files.createTempFile(root, "upload-", ".tmp");
            try {
                long copied = copyWithLimit(file, temporary);
                if (copied > maxBytes) throw new ImageUploadException("The image is larger than the configured upload limit.");
                moveIntoPlace(temporary, target);
                return new StoredImage(target, filename, type.contentType(), copied);
            } catch (RuntimeException | IOException exception) {
                Files.deleteIfExists(temporary);
                if (exception instanceof ImageUploadException imageUploadException) throw imageUploadException;
                throw exception;
            }
        } catch (ImageUploadException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ImageUploadException("The image could not be stored.", exception);
        }
    }

    public StoredImage load(String filename) {
        if (filename == null || !STORED_FILENAME.matcher(filename).matches()) {
            throw new ImageNotFoundException();
        }
        Path path = root.resolve(filename).normalize();
        if (!path.getParent().equals(root) || !Files.isRegularFile(path)) {
            throw new ImageNotFoundException();
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String contentType = switch (extension) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "avif" -> "image/avif";
            default -> "application/octet-stream";
        };
        try {
            return new StoredImage(path, filename, contentType, Files.size(path));
        } catch (IOException exception) {
            throw new ImageNotFoundException();
        }
    }

    private byte[] readHeader(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            return input.readNBytes(HEADER_BYTES);
        }
    }

    private ImageType detectType(byte[] header) {
        for (ImageType type : TYPES.values()) {
            byte[] signature = type.signature();
            if (startsWith(header, signature)) {
                if ("avif".equals(type.extension()) && !containsBrand(header, "avif") && !containsBrand(header, "avis")) continue;
                return type;
            }
        }
        throw new ImageUploadException("Only JPEG, PNG, GIF, WebP, and AVIF images are supported.");
    }

    private boolean containsBrand(byte[] header, String brand) {
        byte[] bytes = brand.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        for (int offset = 0; offset + bytes.length <= header.length; offset++) {
            boolean match = true;
            for (int index = 0; index < bytes.length; index++) {
                if (header[offset + index] != bytes[index]) {
                    match = false;
                    break;
                }
            }
            if (match) return true;
        }
        return false;
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (prefix[index] != 0 && value[index] != prefix[index]) return false;
        }
        return true;
    }

    private long copyWithLimit(MultipartFile file, Path temporary) throws IOException {
        long copied = 0;
        byte[] buffer = new byte[8192];
        try (InputStream input = file.getInputStream(); var output = Files.newOutputStream(temporary)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                copied += read;
                if (copied > maxBytes) return copied;
                output.write(buffer, 0, read);
            }
        }
        return copied;
    }

    private void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record ImageType(String extension, byte[] signature) {
        String contentType() {
            return switch (extension) {
                case "jpg" -> "image/jpeg";
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "webp" -> "image/webp";
                case "avif" -> "image/avif";
                default -> "application/octet-stream";
            };
        }
    }
}
