package com.astropaper.api.articles;

import com.astropaper.api.auth.AccountNotActiveException;
import com.astropaper.api.auth.BlogUserPrincipal;
import com.astropaper.api.auth.PermissionChecker;
import com.astropaper.api.domain.entity.PostEntity;
import com.astropaper.api.domain.entity.TagEntity;
import com.astropaper.api.domain.entity.UserEntity;
import com.astropaper.api.domain.repository.PostRepository;
import com.astropaper.api.domain.repository.TagRepository;
import com.astropaper.api.domain.repository.UserRepository;
import com.astropaper.api.publiccontent.PostNotFoundException;
import com.astropaper.api.publiccontent.dto.PageResponseDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HexFormat;

@Service
@Transactional(readOnly = true)
public class ArticleManagementService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");

    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final PermissionChecker permissionChecker;

    public ArticleManagementService(
        PostRepository postRepository,
        TagRepository tagRepository,
        UserRepository userRepository,
        PermissionChecker permissionChecker
    ) {
        this.postRepository = postRepository;
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
        this.permissionChecker = permissionChecker;
    }

    @PreAuthorize("@permissionChecker.has(authentication, 'post:read')")
    public PageResponseDto<AdminPostSummaryDto> listPosts(
        String status,
        String search,
        int page,
        int size,
        Authentication authentication
    ) {
        UserEntity actor = requireActiveUser(authentication);
        Long authorScope = permissionChecker.isAdministrator(authentication) ? null : actor.getId();
        String normalizedStatus = normalizeStatus(status, true);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<PostEntity> posts = postRepository.findManagementPosts(authorScope, normalizedStatus, normalizedSearch, pageable);
        Page<AdminPostSummaryDto> mapped = posts.map(this::toSummary);
        return toPageResponse(posts, mapped);
    }

    @PreAuthorize("@permissionChecker.has(authentication, 'post:read')")
    public AdminPostDetailDto getPost(Long postId, Authentication authentication) {
        PostEntity post = requirePost(postId);
        requireOwnership(post, authentication);
        return toDetail(post);
    }

    @PreAuthorize("@permissionChecker.has(authentication, 'post:read')")
    public List<AdminTagDto> listTags() {
        return tagRepository.findAllByOrderByNameAsc().stream().map(AdminTagDto::from).toList();
    }

    @Transactional
    @PreAuthorize("@permissionChecker.has(authentication, 'post:create')")
    public AdminPostDetailDto createPost(ManagePostRequest request, Authentication authentication) {
        try {
            UserEntity author = requireActiveUser(authentication);
            List<TagEntity> tags = resolveTags(request.tags());
            PostEntity post = new PostEntity(author);
            applyContent(post, request, author, tags);
            return toDetail(postRepository.saveAndFlush(post));
        } catch (DataIntegrityViolationException exception) {
            throw new ArticleConflictException("The article slug or a tag name is already in use.");
        }
    }

    @Transactional
    @PreAuthorize("@permissionChecker.has(authentication, 'post:update')")
    public AdminPostDetailDto updatePost(Long postId, ManagePostRequest request, Authentication authentication) {
        try {
            PostEntity post = requirePost(postId);
            requireOwnership(post, authentication);
            UserEntity author = post.getAuthor();
            post.updateContent(
                normalizeSlug(request.slug()),
                request.title().trim(),
                request.description().trim(),
                request.contentMarkdown(),
                cleanOptional(request.coverImage()),
                normalizeAuthorName(request.authorName(), author),
                cleanOptional(request.timezone()),
                Boolean.TRUE.equals(request.featured()),
                cleanOptional(request.canonicalURL()),
                cleanOptional(request.ogImage()),
                Boolean.TRUE.equals(request.hideEditPost()),
                resolveTags(request.tags())
            );
            return toDetail(postRepository.saveAndFlush(post));
        } catch (DataIntegrityViolationException exception) {
            throw new ArticleConflictException("The article slug or a tag name is already in use.");
        }
    }

    @Transactional
    public AdminPostDetailDto changePostStatus(Long postId, ChangePostStatusRequest request, Authentication authentication) {
        String status = normalizeStatus(request.status(), false);
        String requiredPermission = switch (status) {
            case "PUBLISHED" -> "post:publish";
            case "ARCHIVED" -> "post:delete";
            default -> "post:update";
        };
        if (!permissionChecker.has(authentication, requiredPermission)) {
            throw new AccessDeniedException("The account cannot change an article to this status.");
        }
        PostEntity post = requirePost(postId);
        requireOwnership(post, authentication);
        post.changeStatus(status);
        return toDetail(postRepository.saveAndFlush(post));
    }

    private void applyContent(PostEntity post, ManagePostRequest request, UserEntity author, List<TagEntity> tags) {
        String slug = normalizeSlug(request.slug());
        post.updateContent(
            slug,
            request.title().trim(),
            request.description().trim(),
            request.contentMarkdown(),
            cleanOptional(request.coverImage()),
            normalizeAuthorName(request.authorName(), author),
            cleanOptional(request.timezone()),
            Boolean.TRUE.equals(request.featured()),
            cleanOptional(request.canonicalURL()),
            cleanOptional(request.ogImage()),
            Boolean.TRUE.equals(request.hideEditPost()),
            tags
        );
    }

    private List<TagEntity> resolveTags(List<String> requestedNames) {
        Map<String, TagEntity> unique = new LinkedHashMap<>();
        if (requestedNames == null) return List.of();
        for (String rawName : requestedNames) {
            if (rawName == null || rawName.isBlank()) continue;
            String name = rawName.trim();
            String key = name.toLowerCase(Locale.ROOT);
            if (unique.containsKey(key)) continue;
            TagEntity tag = tagRepository.findByNameIgnoreCase(name).orElseGet(() -> {
                String slug = tagSlug(name);
                return tagRepository.findBySlug(slug)
                    .filter(existing -> existing.getName().equalsIgnoreCase(name))
                    .orElseGet(() -> {
                        if (tagRepository.findBySlug(slug).isPresent()) {
                            throw new ArticleConflictException("A different tag already uses the slug " + slug + ".");
                        }
                        return tagRepository.saveAndFlush(new TagEntity(slug, name));
                    });
            });
            unique.put(key, tag);
        }
        return new ArrayList<>(unique.values());
    }

    private String tagSlug(String name) {
        String slug = Normalizer.normalize(name, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
        if (!slug.isBlank() && slug.length() <= 100) return slug;
        String asciiFallback = "tag-" + codePointSlug(name);
        return asciiFallback.length() <= 100 ? asciiFallback : "tag-" + sha256Prefix(name);
    }

    private String codePointSlug(String value) {
        return value.codePoints()
            .filter(codePoint -> !Character.isWhitespace(codePoint))
            .mapToObj(codePoint -> "u" + Integer.toHexString(codePoint))
            .collect(java.util.stream.Collectors.joining("-"));
    }

    private String sha256Prefix(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String normalizeStatus(String status, boolean allowBlank) {
        if (status == null || status.isBlank()) {
            if (allowBlank) return null;
            throw new InvalidArticleRequestException("An article status is required.");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new InvalidArticleRequestException("Unsupported article status.");
        return normalized;
    }

    private String normalizeSlug(String rawSlug) {
        String slug = rawSlug.trim().toLowerCase(Locale.ROOT);
        if (slug.endsWith("/") || slug.contains("//")) {
            throw new InvalidArticleRequestException("Article slugs cannot start or end with an empty path segment.");
        }
        return slug;
    }

    private String normalizeAuthorName(String requestedName, UserEntity author) {
        String authorName = cleanOptional(requestedName);
        return authorName == null ? author.getDisplayName() : authorName;
    }

    private String cleanOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private PostEntity requirePost(Long postId) {
        return postRepository.findById(postId).orElseThrow(() -> new PostNotFoundException(String.valueOf(postId)));
    }

    private UserEntity requireActiveUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof BlogUserPrincipal principal)) {
            throw new AccountNotActiveException();
        }
        return userRepository.findById(principal.getId())
            .filter(user -> "ACTIVE".equals(user.getStatus()))
            .orElseThrow(AccountNotActiveException::new);
    }

    private void requireOwnership(PostEntity post, Authentication authentication) {
        if (permissionChecker.isAdministrator(authentication)) return;
        Long actorId = requireActiveUser(authentication).getId();
        if (!post.getAuthor().getId().equals(actorId)) throw new ArticleOwnershipException();
    }

    private AdminPostSummaryDto toSummary(PostEntity post) {
        String authorName = post.getAuthorName() == null ? post.getAuthor().getDisplayName() : post.getAuthorName();
        return new AdminPostSummaryDto(
            post.getId(), post.getSlug(), post.getTitle(), post.getDescription(), post.getStatus(),
            post.getAuthor().getId(), authorName, post.getPublishedAt(), post.getModifiedAt(), post.getUpdatedAt(),
            toTags(post)
        );
    }

    private AdminPostDetailDto toDetail(PostEntity post) {
        String authorName = post.getAuthorName() == null ? post.getAuthor().getDisplayName() : post.getAuthorName();
        return new AdminPostDetailDto(
            post.getId(), post.getSlug(), post.getTitle(), post.getDescription(), post.getContentMarkdown(),
            post.getCoverImageUrl(), post.getStatus(), post.getAuthor().getId(), authorName,
            post.getPublishedAt(), post.getModifiedAt(), post.getTimezone(), post.isFeatured(),
            post.getCanonicalUrl(), post.getOgImageUrl(), post.isHideEditPost(), post.getCreatedAt(),
            post.getUpdatedAt(), toTags(post)
        );
    }

    private List<AdminTagDto> toTags(PostEntity post) {
        return post.getTags().stream()
            .sorted(Comparator.comparing(TagEntity::getName, String.CASE_INSENSITIVE_ORDER))
            .map(AdminTagDto::from)
            .toList();
    }

    private <T> PageResponseDto<T> toPageResponse(Page<?> source, Page<T> mapped) {
        return new PageResponseDto<>(
            mapped.getContent(), mapped.getNumber(), mapped.getSize(), source.getTotalElements(),
            source.getTotalPages(), source.isFirst(), source.isLast()
        );
    }
}
