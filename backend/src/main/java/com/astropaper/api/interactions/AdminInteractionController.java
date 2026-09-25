package com.astropaper.api.interactions;

import com.astropaper.api.publiccontent.dto.PageResponseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Validated
public class AdminInteractionController {

    private final InteractionService interactionService;

    public AdminInteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @GetMapping("/comments")
    public PageResponseDto<AdminCommentDto> listComments(
        @RequestParam(required = false) @Pattern(regexp = "PENDING|PUBLISHED|REJECTED") String status,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return interactionService.listAdminComments(status, page, size);
    }

    @PutMapping("/comments/{commentId}/status")
    public AdminCommentDto changeCommentStatus(
        @PathVariable @Min(1) Long commentId,
        @Valid @RequestBody ChangeCommentStatusRequest request
    ) {
        return interactionService.changeCommentStatus(commentId, request);
    }

    @GetMapping("/messages")
    public PageResponseDto<AdminMessageDto> listMessages(
        @RequestParam(required = false) @Pattern(regexp = "NEW|IN_PROGRESS|RESOLVED|SPAM") String status,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return interactionService.listAdminMessages(status, page, size);
    }

    @PutMapping("/messages/{messageId}/status")
    public AdminMessageDto changeMessageStatus(
        @PathVariable @Min(1) Long messageId,
        @Valid @RequestBody ChangeMessageStatusRequest request
    ) {
        return interactionService.changeMessageStatus(messageId, request);
    }
}
