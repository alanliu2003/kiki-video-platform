package com.kiki.video.api.user.service;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.interaction.mapper.UserFollowMapper;
import com.kiki.video.api.interaction.service.InteractionCounterService;
import com.kiki.video.api.user.dto.CurrentUserResponse;
import com.kiki.video.api.user.dto.OwnerVideoStats;
import com.kiki.video.api.user.dto.PublicProfileResponse;
import com.kiki.video.api.user.mapper.UserMapper;
import com.kiki.video.api.user.model.User;
import com.kiki.video.api.user.model.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final InteractionCounterService counters;

    public UserService(
            UserMapper userMapper,
            UserFollowMapper userFollowMapper,
            InteractionCounterService counters
    ) {
        this.userMapper = userMapper;
        this.userFollowMapper = userFollowMapper;
        this.counters = counters;
    }

    public CurrentUserResponse getCurrentUser(Long userId) {
        return CurrentUserResponse.from(requireActiveUser(userId));
    }

    public PublicProfileResponse getPublicProfile(Long userId, AuthPrincipal principal) {
        User user = requireActiveUser(userId);
        OwnerVideoStats stats = userMapper.videoStatsByOwner(userId);
        if (stats == null) {
            stats = new OwnerVideoStats(0, 0);
        }
        Boolean followed = principal == null
                ? null
                : userFollowMapper.exists(principal.userId(), userId);
        return new PublicProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getCreatedAt(),
                counters.followerCount(userId),
                userFollowMapper.countFollowing(userId),
                stats.publicVideoCount(),
                stats.totalViews(),
                followed
        );
    }

    public User requireActiveUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User was not found");
        }
        return user;
    }
}
