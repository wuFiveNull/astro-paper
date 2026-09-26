package com.astropaper.api.uploads;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1")
public class ImageUploadController {

    private final ImageStorageService imageStorageService;

    public ImageUploadController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @PostMapping(value = "/admin/uploads/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionChecker.has(authentication, 'post:create') or @permissionChecker.has(authentication, 'post:update')")
    public ResponseEntity<ImageUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        ImageUploadResponse response = toResponse(imageStorageService.store(file));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/uploads/images/{filename:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        StoredImage image = imageStorageService.load(filename);
        FileSystemResource resource = new FileSystemResource(image.path());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.contentType()))
            .contentLength(image.size())
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + image.filename() + "\"")
            .body(resource);
    }

    private ImageUploadResponse toResponse(StoredImage image) {
        return new ImageUploadResponse(
            "/api/v1/uploads/images/" + image.filename(),
            image.filename(),
            image.contentType(),
            image.size()
        );
    }
}
