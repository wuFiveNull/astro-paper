package com.astropaper.api.interactions;

import com.astropaper.api.auth.AccountNotActiveException;
import com.astropaper.api.auth.AdminResourceNotFoundException;
import com.astropaper.api.auth.BlogUserPrincipal;
import com.astropaper.api.domain.entity.CommentEntity;
import com.astropaper.api.domain.entity.MessageEntity;
import com.astropaper.api.domain.entity.PostEntity;
import com.astropaper.api.domain.entity.UserEntity;
import com.astropaper.api.domain.repository.CommentRepository;
import com.astropaper.api.domain.repository.MessageRepository;
import com.astropaper.api.domain.repository.PostRepository;
import com.astropaper.api.domain.repository.UserRepository;
import com.astropaper.api.publiccontent.PostNotFoundException;
import com.astropaper.api.publiccontent.dto.PageResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class InteractionService {

    private static final String PUBLISHED = "PUBLISHED";
    private static final String PENDING = "PENDING";
    private static final String NEW = "NEW";
    private static final Set<String> COMMENT_STATUSES = Set.of("PENDING", "PUBLISHED", "REJECTED");
    private static final Set<String> MESSAGE_STATUSES = Set.of("NEW", "IN_PROGRESS", "RESOLVED", "SPAM");

    private final CommentRepository commentRepository;
    private final MessageRepository messageRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public InteractionService(
        CommentRepository commentRepository,
        MessageRepository messageRepository,
        PostRepository postRepository,
        UserRepository userRepository
    ) {
        this.commentRepository = commentRepository;
        this.messageRepository = messageRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    public PageResponseDto<CommentDto> listPublicComments(String postSlug, int page, int size) {
        PostEntity post = requirePublishedPost(postSlug);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<CommentEntity> results = commentRepository.findAllByPostIdAndStatus(post.getId(), PUBLISHED, pageable);
        return toPageResponse(results.map(this::toPublicComment));
    }

    @Transactional
    @PreAuthorize("@permissionChecker.has(authentication, 'comment:create')")
    public CommentSubmissionDto submitComment(SubmitCommentRequest request, Authentication authentication) {
        PostEntity post = requirePublishedPost(request.postSlug().trim());
        UserEntity user = requireActiveUser(authentication);
        CommentEntity parent = request.parentId() == null ? null : commentRepository
            .findByIdAndPostIdAndStatus(request.parentId(), post.getId(), PUBLISHED)
            .orElseThrow(InvalidCommentParentException::new);
        CommentEntity comment = new CommentEntity(post, user, parent, request.body().trim());
        CommentEntity saved = commentRepository.saveAndFlush(comment);
        return new CommentSubmissionDto(saved.getId(), saved.getStatus());
    }

    @PreAuthorize("@permissionChecker.has(authentication, 'comment:moderate')")
    public PageResponseDto<AdminCommentDto> listAdminComments(String status, int page, int size) {
        String normalizedStatus = normalizeFilter(status, COMMENT_STATUSES, "comment");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CommentEntity> results = normalizedStatus == null
            ? commentRepository.findAll(pageable)
            : commentRepository.findAllByStatus(normalizedStatus, pageable);
        return toPageResponse(results.map(this::toAdminComment));
    }

    @Transactional
    @PreAuthorize("@permissionChecker.has(authentication, 'comment:moderate')")
    public AdminCommentDto changeCommentStatus(Long commentId, ChangeCommentStatusRequest request) {
        CommentEntity comment = commentRepository.findWithModerationDetailsById(commentId)
            .orElseThrow(() -> new AdminResourceNotFoundException("The requested comment does not exist."));
        comment.changeStatus(request.status());
        return toAdminComment(commentRepository.saveAndFlush(comment));
    }

    @Transactional
    @PreAuthorize("@permissionChecker.has(authentication, 'message:create')")
    public MessageSubmissionDto submitMessage(SubmitMessageRequest request, Authentication authentication) {
        UserEntity user = requireActiveUser(authentication);
        String subject = request.subject() == null || request.subject().isBlank() ? null : request.subject().trim();
        MessageEntity message = new MessageEntity(
            user,
            user.getDisplayName(),
            user.getEmail(),
            subject,
            request.body().trim()
        );
        MessageEntity saved = messageRepository.saveAndFlush(message);
        return new MessageSubmissionDto(saved.getId(), saved.getStatus());
    }

    @PreAuthorize("@permissionChecker.has(authentication, 'message:read')")
    public PageResponseDto<AdminMessageDto> listAdminMessages(String status, int page, int size) {
        String normalizedStatus = normalizeFilter(status, MESSAGE_STATUSES, "message");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MessageEntity> results = normalizedStatus == null
            ? messageRepository.findAll(pageable)
            : messageRepository.findAllByStatus(normalizedStatus, pageable);
        return toPageResponse(results.map(this::toAdminMessage));
    }

    @Transactional
    @PreAuthorize("@permissionChecker.has(authentication, 'message:update')")
    public AdminMessageDto changeMessageStatus(Long messageId, ChangeMessageStatusRequest request) {
        MessageEntity message = messageRepository.findWithUserById(messageId)
            .orElseThrow(() -> new AdminResourceNotFoundException("The requested message does not exist."));
        message.changeStatus(request.status());
        return toAdminMessage(messageRepository.saveAndFlush(message));
    }

    private PostEntity requirePublishedPost(String slug) {
        return postRepository.findBySlugAndStatusAndPublishedAtLessThanEqual(slug, PUBLISHED, Instant.now())
            .orElseThrow(() -> new PostNotFoundException(slug));
    }

    private UserEntity requireActiveUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof BlogUserPrincipal principal)) {
            throw new AccountNotActiveException();
        }
        return userRepository.findById(principal.getId())
            .filter(user -> "ACTIVE".equals(user.getStatus()))
            .orElseThrow(AccountNotActiveException::new);
    }

    private String normalizeFilter(String status, Set<String> allowedStatuses, String type) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!allowedStatuses.contains(normalized)) {
            throw new InvalidInteractionStatusException("Unsupported " + type + " status.");
        }
        return normalized;
    }

    private CommentDto toPublicComment(CommentEntity comment) {
        return new CommentDto(
            comment.getId(),
            comment.getParent() == null ? null : comment.getParent().getId(),
            comment.getUser().getDisplayName(),
            comment.getBody(),
            comment.getCreatedAt()
        );
    }

    private AdminCommentDto toAdminComment(CommentEntity comment) {
        return new AdminCommentDto(
            comment.getId(),
            comment.getPost().getSlug(),
            comment.getPost().getTitle(),
            comment.getParent() == null ? null : comment.getParent().getId(),
            comment.getUser().getUsername(),
            comment.getUser().getDisplayName(),
            comment.getBody(),
            comment.getStatus(),
            comment.getCreatedAt()
        );
    }

    private AdminMessageDto toAdminMessage(MessageEntity message) {
        return new AdminMessageDto(
            message.getId(),
            message.getUser().getId(),
            message.getUser().getUsername(),
            message.getSenderName(),
            message.getSenderEmail(),
            message.getSubject(),
            message.getBody(),
            message.getStatus(),
            message.getCreatedAt(),
            message.getUpdatedAt()
        );
    }

    private <T> PageResponseDto<T> toPageResponse(Page<T> results) {
        return new PageResponseDto<>(
            results.getContent(),
            results.getNumber(),
            results.getSize(),
            results.getTotalElements(),
            results.getTotalPages(),
            results.isFirst(),
            results.isLast()
        );
    }
}
