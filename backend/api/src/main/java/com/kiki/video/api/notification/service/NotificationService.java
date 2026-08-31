package com.kiki.video.api.notification.service;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.notification.NotificationSnippets;
import com.kiki.video.api.notification.dto.NotificationActorResponse;
import com.kiki.video.api.notification.dto.NotificationCommentResponse;
import com.kiki.video.api.notification.dto.NotificationListResponse;
import com.kiki.video.api.notification.dto.NotificationResponse;
import com.kiki.video.api.notification.dto.NotificationUnreadCountResponse;
import com.kiki.video.api.notification.dto.NotificationVideoResponse;
import com.kiki.video.api.notification.mapper.NotificationMapper;
import com.kiki.video.api.notification.model.Notification;
import com.kiki.video.api.notification.model.NotificationRow;
import com.kiki.video.api.notification.model.NotificationType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;

    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    public void createIfNotSelf(
            Long recipientUserId,
            Long actorUserId,
            NotificationType type,
            Long videoId,
            Long commentId,
            Long parentCommentId
    ) {
        if (recipientUserId == null || actorUserId == null || type == null) {
            return;
        }
        if (recipientUserId.equals(actorUserId)) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipientUserId(recipientUserId);
        notification.setActorUserId(actorUserId);
        notification.setType(type);
        notification.setVideoId(videoId);
        notification.setCommentId(commentId);
        notification.setParentCommentId(parentCommentId);
        notification.setRead(false);
        notification.setCreatedAt(Instant.now());
        notificationMapper.insert(notification);
    }

    public NotificationListResponse list(AuthPrincipal principal, Integer page, Integer size) {
        PageBounds bounds = bounds(page, size);
        List<NotificationResponse> items = notificationMapper
                .findPageByRecipient(principal.userId(), bounds.size, bounds.offset)
                .stream()
                .map(this::toResponse)
                .toList();
        long total = notificationMapper.countByRecipient(principal.userId());
        return new NotificationListResponse(items, bounds.page, bounds.size, total);
    }

    public NotificationUnreadCountResponse unreadCount(AuthPrincipal principal) {
        return new NotificationUnreadCountResponse(notificationMapper.countUnreadByRecipient(principal.userId()));
    }

    @Transactional
    public NotificationUnreadCountResponse markRead(long notificationId, AuthPrincipal principal) {
        Notification notification = notificationMapper.findById(notificationId);
        if (notification == null || !principal.userId().equals(notification.getRecipientUserId())) {
            throw new ApiException(ErrorCode.NOTIFICATION_NOT_FOUND, HttpStatus.NOT_FOUND, "Notification was not found");
        }
        notificationMapper.markRead(notificationId, principal.userId());
        return unreadCount(principal);
    }

    @Transactional
    public NotificationUnreadCountResponse markAllRead(AuthPrincipal principal) {
        notificationMapper.markAllRead(principal.userId());
        return new NotificationUnreadCountResponse(0);
    }

    private NotificationResponse toResponse(NotificationRow row) {
        return new NotificationResponse(
                row.getId(),
                row.getType(),
                row.isRead(),
                row.getCreatedAt(),
                actor(row),
                video(row),
                comment(row)
        );
    }

    private static NotificationActorResponse actor(NotificationRow row) {
        if (row.getActorUserId() == null) {
            return null;
        }
        return new NotificationActorResponse(row.getActorUserId(), row.getActorUsername(), row.getActorDisplayName());
    }

    private static NotificationVideoResponse video(NotificationRow row) {
        if (row.getVideoId() == null) {
            return null;
        }
        return new NotificationVideoResponse(
                row.getVideoId(),
                row.getVideoTitle(),
                "/api/videos/" + row.getVideoId() + "/thumbnail"
        );
    }

    private static NotificationCommentResponse comment(NotificationRow row) {
        if (row.getCommentId() == null) {
            return null;
        }
        return new NotificationCommentResponse(row.getCommentId(), NotificationSnippets.snippet(row.getCommentContent()));
    }

    static PageBounds bounds(Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return new PageBounds(safePage, safeSize, safePage * safeSize);
    }

    record PageBounds(int page, int size, int offset) {
    }
}
