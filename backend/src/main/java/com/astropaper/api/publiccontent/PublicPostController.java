package com.astropaper.api.publiccontent;

import com.astropaper.api.publiccontent.dto.PageResponseDto;
import com.astropaper.api.publiccontent.dto.PostDetailDto;
import com.astropaper.api.publiccontent.dto.PostSummaryDto;
import com.astropaper.api.publiccontent.dto.TagDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Validated
public class PublicPostController {

    private final PublicContentService publicContentService;

    public PublicPostController(PublicContentService publicContentService) {
        this.publicContentService = publicContentService;
    }

    @GetMapping("/posts")
    public PageResponseDto<PostSummaryDto> listPosts(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
        @RequestParam(required = false) @Size(max = 100) String q
    ) {
        return publicContentService.listPosts(page, size, q);
    }

    @GetMapping("/posts/{*slug}")
    public PostDetailDto getPost(@PathVariable("slug") @Size(max = 180) String slug) {
        String normalizedSlug = slug.startsWith("/") ? slug.substring(1) : slug;
        return publicContentService.getPost(normalizedSlug);
    }

    @GetMapping("/tags")
    public List<TagDto> listTags() {
        return publicContentService.listTags();
    }

    @GetMapping("/tags/{tagSlug}/posts")
    public PageResponseDto<PostSummaryDto> listPostsByTag(
        @PathVariable @Size(max = 100) String tagSlug,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        return publicContentService.listPostsByTag(tagSlug, page, size);
    }

    @GetMapping("/search")
    public PageResponseDto<PostSummaryDto> search(
        @RequestParam @Size(min = 1, max = 100) String q,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        return publicContentService.listPosts(page, size, q);
    }
}
