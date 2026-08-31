-- Authenticated qualified-view history for recommendations.
-- M9 video_view_idempotency has no user_id and is not a watch log.
-- Likes, favorites, comments, and follows remain the source of truth
-- for those signals; this table only records authenticated qualifies.
CREATE TABLE user_video_qualified_views (
    user_id                 BIGINT NOT NULL,
    video_id                BIGINT NOT NULL,
    qualified_view_count    INTEGER NOT NULL DEFAULT 1,
    last_qualified_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT user_video_qualified_views_pkey
        PRIMARY KEY (user_id, video_id),
    CONSTRAINT user_video_qualified_views_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT user_video_qualified_views_video_id_fkey
        FOREIGN KEY (video_id) REFERENCES videos (id),
    CONSTRAINT user_video_qualified_views_count_check
        CHECK (qualified_view_count >= 1)
);

CREATE INDEX user_video_qualified_views_user_recent_idx
    ON user_video_qualified_views (user_id, last_qualified_at DESC);

-- Bounded "recent interactions by this user" scans for affinity.
CREATE INDEX video_likes_user_created_idx
    ON video_likes (user_id, created_at DESC);

CREATE INDEX video_favorites_user_created_idx
    ON video_favorites (user_id, created_at DESC);

CREATE INDEX comments_author_created_idx
    ON comments (author_user_id, created_at DESC);
