package com.astropaper.api.publiccontent;

import com.astropaper.api.domain.entity.PostEntity;
import com.astropaper.api.domain.repository.PostRepository;
import com.astropaper.api.domain.repository.TagRepository;
import com.astropaper.api.publiccontent.dto.PageResponseDto;
import com.astropaper.api.publiccontent.dto.PostDetailDto;
import com.astropaper.api.publiccontent.dto.PostSummaryDto;
import com.astropaper.api.publiccontent.dto.TagDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PublicContentService {

    private static final String PUBLISHED = "PUBLISHED";

    private final PostRepository postRepository;
    private final TagRepository tagRepository;

    public PublicContentService(PostRepository postRepository, TagRepository tagRepository) {
        this.postRepository = postRepository;
        this.tagRepository = tagRepository;
    }

    public PageResponseDto<PostSummaryDto> listPosts(int page, int size, String searchTerm) {
        String normalizedTerm = searchTerm == null || searchTerm.isBlank() ? null : searchTerm.trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt"));
        Page<PostEntity> results = postRepository.findPublicPosts(normalizedTerm, Instant.now(), pageable);
        return toPageResponse(results, results.map(this::toSummary));
    }

    public PostDetailDto getPost(String slug) {
        PostEntity post = postRepository.findBySlugAndStatusAndPublishedAtLessThanEqual(
            slug, PUBLISHED, Instant.now()
        ).orElseThrow(() -> new PostNotFoundException(slug));
        return new PostDetailDto(
            post.getSlug(),
            post.getTitle(),
            post.getDescription(),
            authorName(post),
            post.getPublishedAt(),
            post.getModifiedAt(),
            post.getTimezone(),
            tagNames(post),
            post.isFeatured(),
            post.getCoverImageUrl(),
            post.getCanonicalUrl(),
            post.getOgImageUrl(),
            post.isHideEditPost(),
            post.getContentMarkdown()
        );
    }

    public List<TagDto> listTags() {
        return tagRepository.findPublicTagCounts(Instant.now()).stream()
            .map(tag -> new TagDto(tag.getSlug(), tag.getName(), tag.getPostCount()))
            .toList();
    }

    public PageResponseDto<PostSummaryDto> listPostsByTag(String tagSlug, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt"));
        Page<PostEntity> results = postRepository.findPublicPostsByTag(tagSlug, Instant.now(), pageable);
        return toPageResponse(results, results.map(this::toSummary));
    }

    private PageResponseDto<PostSummaryDto> toPageResponse(
        Page<PostEntity> source,
        Page<PostSummaryDto> mapped
    ) {
        return new PageResponseDto<>(
            mapped.getContent(),
            mapped.getNumber(),
            mapped.getSize(),
            source.getTotalElements(),
            source.getTotalPages(),
            source.isFirst(),
            source.isLast()
        );
    }

    private PostSummaryDto toSummary(PostEntity post) {
        return new PostSummaryDto(
            post.getSlug(),
            post.getTitle(),
            post.getDescription(),
            authorName(post),
            post.getPublishedAt(),
            post.getModifiedAt(),
            post.getTimezone(),
            tagNames(post),
            post.isFeatured(),
            post.getCoverImageUrl(),
            post.getCanonicalUrl(),
            post.getOgImageUrl(),
            post.isHideEditPost()
        );
    }

    private String authorName(PostEntity post) {
        String name = post.getAuthorName();
        return name == null || name.isBlank() ? post.getAuthor().getDisplayName() : name;
    }

    private List<String> tagNames(PostEntity post) {
        return post.getTags().stream()
            .map(tag -> tag.getName())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }
}
