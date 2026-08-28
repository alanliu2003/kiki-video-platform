package com.kiki.video.api.interaction.service;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.interaction.dto.CreatorRelationshipResponse;
import com.kiki.video.api.interaction.mapper.UserFollowMapper;
import com.kiki.video.api.interaction.model.UserFollow;
import com.kiki.video.api.user.mapper.UserMapper;
import com.kiki.video.api.user.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class FollowService {

    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final InteractionCounterService counters;

    public FollowService(
            UserMapper userMapper,
            UserFollowMapper userFollowMapper,
            InteractionCounterService counters
    ) {
        this.userMapper = userMapper;
        this.userFollowMapper = userFollowMapper;
        this.counters = counters;
    }

    public CreatorRelationshipResponse relationship(Long userId, AuthPrincipal principal) {
        requireUser(userId);
        long followerCount = counters.followerCount(userId);
        boolean followed = principal != null && userFollowMapper.exists(principal.userId(), userId);
        return new CreatorRelationshipResponse(followerCount, followed);
    }

    @Transactional
    public CreatorRelationshipResponse follow(Long userId, AuthPrincipal principal) {
        requireUser(userId);
        if (principal.userId().equals(userId)) {
            throw new ApiException(
                    ErrorCode.SELF_FOLLOW_NOT_ALLOWED,
                    HttpStatus.BAD_REQUEST,
                    "You cannot follow yourself"
            );
        }
        UserFollow follow = new UserFollow();
        follow.setFollowerUserId(principal.userId());
        follow.setFollowedUserId(userId);
        follow.setCreatedAt(Instant.now());
        int inserted = userFollowMapper.insertIgnore(follow);
        if (inserted > 0) {
            AfterCommit.run(() -> counters.onFollowCreated(userId));
        }
        return relationshipFromDatabase(userId, principal);
    }

    @Transactional
    public CreatorRelationshipResponse unfollow(Long userId, AuthPrincipal principal) {
        requireUser(userId);
        int deleted = userFollowMapper.delete(principal.userId(), userId);
        if (deleted > 0) {
            AfterCommit.run(() -> counters.onFollowRemoved(userId));
        }
        return relationshipFromDatabase(userId, principal);
    }

    private CreatorRelationshipResponse relationshipFromDatabase(Long userId, AuthPrincipal principal) {
        long followerCount = userFollowMapper.countFollowers(userId);
        boolean followed = principal != null && userFollowMapper.exists(principal.userId(), userId);
        return new CreatorRelationshipResponse(followerCount, followed);
    }

    private User requireUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User was not found");
        }
        return user;
    }
}
