package com.kiki.video.api.interaction.cache;

public final class RedisKeys {

    public static final String PREFIX = "kiki:";

    private RedisKeys() {
    }

    public static String likeCount(long videoId) {
        return PREFIX + "video:" + videoId + ":like-count";
    }

    public static String favoriteCount(long videoId) {
        return PREFIX + "video:" + videoId + ":favorite-count";
    }

    public static String commentCount(long videoId) {
        return PREFIX + "video:" + videoId + ":comment-count";
    }

    public static String followerCount(long userId) {
        return PREFIX + "user:" + userId + ":follower-count";
    }

    public static String commentRateLimit(long userId) {
        return PREFIX + "ratelimit:comment:" + userId;
    }

    public static String danmakuRateLimit(long userId) {
        return PREFIX + "ratelimit:danmaku:" + userId;
    }
}
