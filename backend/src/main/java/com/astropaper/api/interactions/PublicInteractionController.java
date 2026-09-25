package com.astropaper.api.interactions;

import com.astropaper.api.publiccontent.dto.PageResponseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
public class PublicInteractionController {

    private final InteractionService interactionService;

    public PublicInteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @GetMapping("/comments")
    public PageResponseDto<CommentDto> listComments(
        @RequestParam @NotBlank @Size(max = 180) String postSlug,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        return interactionService.listPublicComments(postSlug, page, size);
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentSubmissionDto submitComment(
        @Valid @RequestBody SubmitCommentRequest request,
        Authentication authentication
    ) {
        return interactionService.submitComment(request, authentication);
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageSubmissionDto submitMessage(
        @Valid @RequestBody SubmitMessageRequest request,
        Authentication authentication
    ) {
        return interactionService.submitMessage(request, authentication);
    }
}
