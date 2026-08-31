package com.kiki.video.api.interaction.service;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.interaction.dto.VideoInteractionResponse;
import com.kiki.video.api.interaction.mapper.CommentMapper;
import com.kiki.video.api.interaction.mapper.VideoFavoriteMapper;
import com.kiki.video.api.interaction.mapper.VideoLikeMapper;
import com.kiki.video.api.interaction.model.VideoFavorite;
import com.kiki.video.api.interaction.model.VideoInteractionCounts;
import com.kiki.video.api.interaction.model.VideoLike;
import com.kiki.video.api.interaction.model.VideoViewerState;
import com.kiki.video.api.notification.model.NotificationType;
import com.kiki.video.api.notification.service.NotificationService;
import com.kiki.video.api.video.mapper.VideoMapper;
import com.kiki.video.api.video.model.Video;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class VideoInteractionService {

    private final VideoMapper videoMapper;
    private final VideoLikeMapper videoLikeMapper;
    private final VideoFavoriteMapper videoFavoriteMapper;
    private final CommentMapper commentMapper;
    private final InteractionCounterService counters;
    private final NotificationService notifications;

    public VideoInteractionService(
            VideoMapper videoMapper,
            VideoLikeMapper videoLikeMapper,
            VideoFavoriteMapper videoFavoriteMapper,
            CommentMapper commentMapper,
            InteractionCounterService counters,
            NotificationService notifications
    ) {
        this.videoMapper = videoMapper;
        this.videoLikeMapper = videoLikeMapper;
        this.videoFavoriteMapper = videoFavoriteMapper;
        this.commentMapper = commentMapper;
        this.counters = counters;
        this.notifications = notifications;
    }

    public VideoInteractionResponse summary(Long videoId, AuthPrincipal principal) {
        requireVideo(videoId);
        return toResponse(counters.videoCounts(videoId), videoId, principal);
    }

    @Transactional
    public VideoInteractionResponse like(Long videoId, AuthPrincipal principal) {
        Video video = requireVideo(videoId);
        VideoLike like = new VideoLike();
        like.setUserId(principal.userId());
        like.setVideoId(videoId);
        like.setCreatedAt(Instant.now());
        int inserted = videoLikeMapper.insertIgnore(like);
        if (inserted > 0) {
            notifications.createIfNotSelf(
                    video.getOwnerUserId(),
                    principal.userId(),
                    NotificationType.VIDEO_LIKED,
                    videoId,
                    null,
                    null
            );
            AfterCommit.run(() -> counters.onLikeCreated(videoId));
        }
        return summaryFromDatabase(videoId, principal);
    }

    @Transactional
    public VideoInteractionResponse unlike(Long videoId, AuthPrincipal principal) {
        requireVideo(videoId);
        int deleted = videoLikeMapper.delete(principal.userId(), videoId);
        if (deleted > 0) {
            AfterCommit.run(() -> counters.onLikeRemoved(videoId));
        }
        return summaryFromDatabase(videoId, principal);
    }

    @Transactional
    public VideoInteractionResponse favorite(Long videoId, AuthPrincipal principal) {
        Video video = requireVideo(videoId);
        VideoFavorite favorite = new VideoFavorite();
        favorite.setUserId(principal.userId());
        favorite.setVideoId(videoId);
        favorite.setCreatedAt(Instant.now());
        int inserted = videoFavoriteMapper.insertIgnore(favorite);
        if (inserted > 0) {
            notifications.createIfNotSelf(
                    video.getOwnerUserId(),
                    principal.userId(),
                    NotificationType.VIDEO_FAVORITED,
                    videoId,
                    null,
                    null
            );
            AfterCommit.run(() -> counters.onFavoriteCreated(videoId));
        }
        return summaryFromDatabase(videoId, principal);
    }

    @Transactional
    public VideoInteractionResponse unfavorite(Long videoId, AuthPrincipal principal) {
        requireVideo(videoId);
        int deleted = videoFavoriteMapper.delete(principal.userId(), videoId);
        if (deleted > 0) {
            AfterCommit.run(() -> counters.onFavoriteRemoved(videoId));
        }
        return summaryFromDatabase(videoId, principal);
    }

    private VideoInteractionResponse summaryFromDatabase(Long videoId, AuthPrincipal principal) {
        VideoInteractionCounts counts = commentMapper.countVideoInteractions(videoId);
        return toResponse(counts, videoId, principal);
    }

    private VideoInteractionResponse toResponse(
            VideoInteractionCounts counts,
            Long videoId,
            AuthPrincipal principal
    ) {
        boolean liked = false;
        boolean favorited = false;
        if (principal != null) {
            VideoViewerState state = commentMapper.findViewerState(principal.userId(), videoId);
            liked = state.liked();
            favorited = state.favorited();
        }
        return new VideoInteractionResponse(
                counts.likeCount(),
                counts.favoriteCount(),
                counts.commentCount(),
                liked,
                favorited
        );
    }

    private Video requireVideo(Long videoId) {
        Video video = videoMapper.findById(videoId);
        if (video == null) {
            throw new ApiException(ErrorCode.VIDEO_NOT_FOUND, HttpStatus.NOT_FOUND, "Video was not found");
        }
        return video;
    }
}
