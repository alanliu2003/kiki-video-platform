package com.kiki.video.api.notification.controller;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.notification.dto.NotificationListResponse;
import com.kiki.video.api.notification.dto.NotificationUnreadCountResponse;
import com.kiki.video.api.notification.service.NotificationService;
import com.kiki.video.common.ApiConstants;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_PREFIX + "/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationListResponse list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return notificationService.list(principal, page, size);
    }

    @GetMapping("/unread-count")
    public NotificationUnreadCountResponse unreadCount(@AuthenticationPrincipal AuthPrincipal principal) {
        return notificationService.unreadCount(principal);
    }

    @PostMapping("/{id}/read")
    public NotificationUnreadCountResponse markRead(
            @PathVariable("id") long id,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return notificationService.markRead(id, principal);
    }

    @PostMapping("/read-all")
    public NotificationUnreadCountResponse markAllRead(@AuthenticationPrincipal AuthPrincipal principal) {
        return notificationService.markAllRead(principal);
    }
}
