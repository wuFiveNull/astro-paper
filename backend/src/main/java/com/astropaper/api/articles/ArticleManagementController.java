package com.astropaper.api.articles;

import com.astropaper.api.publiccontent.dto.PageResponseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@Validated
public class ArticleManagementController {

    private final ArticleManagementService articleManagementService;

    public ArticleManagementController(ArticleManagementService articleManagementService) {
        this.articleManagementService = articleManagementService;
    }

    @GetMapping("/posts")
    public PageResponseDto<AdminPostSummaryDto> listPosts(
        @RequestParam(required = false) @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED") String status,
        @RequestParam(required = false) @Size(max = 100) String q,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        Authentication authentication
    ) {
        return articleManagementService.listPosts(status, q, page, size, authentication);
    }

    @GetMapping("/posts/{postId}")
    public AdminPostDetailDto getPost(@PathVariable @Min(1) Long postId, Authentication authentication) {
        return articleManagementService.getPost(postId, authentication);
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminPostDetailDto createPost(
        @Valid @RequestBody ManagePostRequest request,
        Authentication authentication
    ) {
        return articleManagementService.createPost(request, authentication);
    }

    @PutMapping("/posts/{postId}")
    public AdminPostDetailDto updatePost(
        @PathVariable @Min(1) Long postId,
        @Valid @RequestBody ManagePostRequest request,
        Authentication authentication
    ) {
        return articleManagementService.updatePost(postId, request, authentication);
    }

    @PutMapping("/posts/{postId}/status")
    public AdminPostDetailDto changePostStatus(
        @PathVariable @Min(1) Long postId,
        @Valid @RequestBody ChangePostStatusRequest request,
        Authentication authentication
    ) {
        return articleManagementService.changePostStatus(postId, request, authentication);
    }

    @GetMapping("/tags")
    public List<AdminTagDto> listTags() {
        return articleManagementService.listTags();
    }
}
