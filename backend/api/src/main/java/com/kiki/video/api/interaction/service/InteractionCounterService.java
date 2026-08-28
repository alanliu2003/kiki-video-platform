package com.kiki.video.api.interaction.service;

import com.kiki.video.api.config.InteractionProperties;
import com.kiki.video.api.interaction.cache.InteractionRedisClient;
import com.kiki.video.api.interaction.cache.RedisKeys;
import com.kiki.video.api.interaction.mapper.CommentMapper;
import com.kiki.video.api.interaction.mapper.UserFollowMapper;
import com.kiki.video.api.interaction.model.VideoInteractionCounts;
import org.springframework.stereotype.Service;

@Service
public class InteractionCounterService {

    private final CommentMapper commentMapper;
    private final UserFollowMapper userFollowMapper;
    private final InteractionRedisClient redis;
    private final InteractionProperties properties;

    public InteractionCounterService(
            CommentMapper commentMapper,
            UserFollowMapper userFollowMapper,
            InteractionRedisClient redis,
            InteractionProperties properties
    ) {
        this.commentMapper = commentMapper;
        this.userFollowMapper = userFollowMapper;
        this.redis = redis;
        this.properties = properties;
    }

    public VideoInteractionCounts videoCounts(long videoId) {
        var likeKey = RedisKeys.likeCount(videoId);
        var favoriteKey = RedisKeys.favoriteCount(videoId);
        var commentKey = RedisKeys.commentCount(videoId);

        var likes = redis.getCount(likeKey);
        var favorites = redis.getCount(favoriteKey);
        var comments = redis.getCount(commentKey);
        if (likes.isPresent() && favorites.isPresent() && comments.isPresent()) {
            return new VideoInteractionCounts(likes.get(), favorites.get(), comments.get());
        }

        VideoInteractionCounts counts = commentMapper.countVideoInteractions(videoId);
        redis.setCount(likeKey, counts.likeCount(), properties.ttl());
        redis.setCount(favoriteKey, counts.favoriteCount(), properties.ttl());
        redis.setCount(commentKey, counts.commentCount(), properties.ttl());
        return counts;
    }

    public long followerCount(long userId) {
        var key = RedisKeys.followerCount(userId);
        return redis.getCount(key).orElseGet(() -> {
            long count = userFollowMapper.countFollowers(userId);
            redis.setCount(key, count, properties.ttl());
            return count;
        });
    }

    public void onLikeCreated(long videoId) {
        adjust(RedisKeys.likeCount(videoId), 1, videoId);
    }

    public void onLikeRemoved(long videoId) {
        adjust(RedisKeys.likeCount(videoId), -1, videoId);
    }

    public void onFavoriteCreated(long videoId) {
        adjust(RedisKeys.favoriteCount(videoId), 1, videoId);
    }

    public void onFavoriteRemoved(long videoId) {
        adjust(RedisKeys.favoriteCount(videoId), -1, videoId);
    }

    public void onCommentCreated(long videoId) {
        adjust(RedisKeys.commentCount(videoId), 1, videoId);
    }

    public void onFollowCreated(long followedUserId) {
        if (!applyDelta(RedisKeys.followerCount(followedUserId), 1)) {
            refreshFollowerCount(followedUserId);
        }
    }

    public void onFollowRemoved(long followedUserId) {
        if (!applyDelta(RedisKeys.followerCount(followedUserId), -1)) {
            refreshFollowerCount(followedUserId);
        }
    }

    private void adjust(String key, int delta, long videoId) {
        if (!applyDelta(key, delta)) {
            refreshVideoCounts(videoId);
        }
    }

    private boolean applyDelta(String key, int delta) {
        if (delta > 0) {
            return redis.incrementIfPresent(key, properties.ttl());
        }
        return redis.decrementIfPresent(key, properties.ttl());
    }

    private void refreshVideoCounts(long videoId) {
        VideoInteractionCounts counts = commentMapper.countVideoInteractions(videoId);
        redis.setCount(RedisKeys.likeCount(videoId), counts.likeCount(), properties.ttl());
        redis.setCount(RedisKeys.favoriteCount(videoId), counts.favoriteCount(), properties.ttl());
        redis.setCount(RedisKeys.commentCount(videoId), counts.commentCount(), properties.ttl());
    }

    private void refreshFollowerCount(long userId) {
        redis.setCount(RedisKeys.followerCount(userId), userFollowMapper.countFollowers(userId), properties.ttl());
    }
}
