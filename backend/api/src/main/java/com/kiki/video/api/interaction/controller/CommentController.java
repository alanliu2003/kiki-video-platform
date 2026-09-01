package com.kiki.video.api.interaction.controller;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.interaction.dto.CommentListResponse;
import com.kiki.video.api.interaction.dto.CommentResponse;
import com.kiki.video.api.interaction.dto.CreateCommentRequest;
import com.kiki.video.api.interaction.service.CommentService;
import com.kiki.video.api.openapi.OpenApiTags;
import com.kiki.video.common.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_PREFIX + "/videos/{videoId}/comments")
@Tag(name = OpenApiTags.COMMENTS)
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    @Operation(summary = "List comments")
    public CommentListResponse list(
            @PathVariable Long videoId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return commentService.list(videoId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Create a comment or reply")
    public CommentResponse create(
            @PathVariable Long videoId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        return commentService.create(videoId, principal, request);
    }
}
