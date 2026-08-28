package com.kiki.video.api.interaction.service;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.config.InteractionProperties;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.interaction.cache.InteractionRedisClient;
import com.kiki.video.api.interaction.cache.RedisKeys;
import com.kiki.video.api.interaction.dto.CommentListResponse;
import com.kiki.video.api.interaction.dto.CommentResponse;
import com.kiki.video.api.interaction.dto.CreateCommentRequest;
import com.kiki.video.api.interaction.mapper.CommentMapper;
import com.kiki.video.api.interaction.model.Comment;
import com.kiki.video.api.interaction.model.CommentStatus;
import com.kiki.video.api.user.mapper.UserMapper;
import com.kiki.video.api.user.model.User;
import com.kiki.video.api.video.mapper.VideoMapper;
import com.kiki.video.api.video.model.Video;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommentService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_CONTENT_LENGTH = 2000;

    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final CommentMapper commentMapper;
    private final InteractionCounterService counters;
    private final InteractionRedisClient redis;
    private final InteractionProperties properties;

    public CommentService(
            VideoMapper videoMapper,
            UserMapper userMapper,
            CommentMapper commentMapper,
            InteractionCounterService counters,
            InteractionRedisClient redis,
            InteractionProperties properties
    ) {
        this.videoMapper = videoMapper;
        this.userMapper = userMapper;
        this.commentMapper = commentMapper;
        this.counters = counters;
        this.redis = redis;
        this.properties = properties;
    }

    public CommentListResponse list(Long videoId, Integer page, Integer size) {
        requireVideo(videoId);
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int offset = safePage * safeSize;

        List<Comment> topLevel = commentMapper.findTopLevelByVideoId(videoId, safeSize, offset);
        long total = commentMapper.countTopLevelByVideoId(videoId);
        if (topLevel.isEmpty()) {
            return new CommentListResponse(List.of(), safePage, safeSize, total);
        }

        List<Long> parentIds = topLevel.stream().map(Comment::getId).toList();
        List<Comment> replies = commentMapper.findRepliesByParentIds(parentIds);
        Map<Long, List<CommentResponse>> repliesByParent = new LinkedHashMap<>();
        for (Comment reply : replies) {
            repliesByParent
                    .computeIfAbsent(reply.getParentCommentId(), ignored -> new ArrayList<>())
                    .add(CommentResponse.from(reply));
        }

        List<CommentResponse> items = topLevel.stream()
                .map(comment -> CommentResponse.from(
                        comment,
                        repliesByParent.getOrDefault(comment.getId(), List.of())
                ))
                .toList();
        return new CommentListResponse(items, safePage, safeSize, total);
    }

    @Transactional
    public CommentResponse create(Long videoId, AuthPrincipal principal, CreateCommentRequest request) {
        requireVideo(videoId);
        User author = requireUser(principal.userId());
        String content = normalizeContent(request == null ? null : request.content());
        Long parentCommentId = request == null ? null : request.parentCommentId();
        if (parentCommentId != null) {
            Comment parent = commentMapper.findById(parentCommentId);
            if (parent == null || parent.getStatus() != CommentStatus.ACTIVE) {
                throw new ApiException(ErrorCode.COMMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "Parent comment was not found");
            }
            if (!videoId.equals(parent.getVideoId())) {
                throw new ApiException(
                        ErrorCode.INVALID_COMMENT_PARENT,
                        HttpStatus.BAD_REQUEST,
                        "Parent comment does not belong to this video"
                );
            }
            if (parent.getParentCommentId() != null) {
                throw new ApiException(
                        ErrorCode.INVALID_COMMENT_PARENT,
                        HttpStatus.BAD_REQUEST,
                        "Replies are only allowed on top-level comments"
                );
            }
        }
        enforceRateLimit(principal.userId());

        Instant now = Instant.now();
        Comment comment = new Comment();
        comment.setVideoId(videoId);
        comment.setAuthorUserId(author.getId());
        comment.setParentCommentId(parentCommentId);
        comment.setContent(content);
        comment.setStatus(CommentStatus.ACTIVE);
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        commentMapper.insert(comment);
        comment.setAuthorUsername(author.getUsername());
        comment.setAuthorDisplayName(author.getDisplayName());
        AfterCommit.run(() -> counters.onCommentCreated(videoId));
        return CommentResponse.from(comment);
    }

    private void enforceRateLimit(Long userId) {
        redis.incrementRate(RedisKeys.commentRateLimit(userId), properties.commentRateWindow())
                .ifPresent(count -> {
                    if (count > properties.commentRateLimit()) {
                        throw new ApiException(
                                ErrorCode.RATE_LIMITED,
                                HttpStatus.TOO_MANY_REQUESTS,
                                "Too many comments; try again in a minute"
                        );
                    }
                });
    }

    private Video requireVideo(Long videoId) {
        Video video = videoMapper.findById(videoId);
        if (video == null) {
            throw new ApiException(ErrorCode.VIDEO_NOT_FOUND, HttpStatus.NOT_FOUND, "Video was not found");
        }
        return video;
    }

    private User requireUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User was not found");
        }
        return user;
    }

    private static String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_COMMENT, HttpStatus.BAD_REQUEST, "Content is required");
        }
        String trimmed = content.trim();
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new ApiException(
                    ErrorCode.INVALID_COMMENT,
                    HttpStatus.BAD_REQUEST,
                    "Content must be at most 2000 characters"
            );
        }
        return trimmed;
    }
}
