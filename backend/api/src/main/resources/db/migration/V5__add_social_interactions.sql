CREATE TABLE video_likes (
    user_id     BIGINT NOT NULL,
    video_id    BIGINT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT video_likes_pkey PRIMARY KEY (user_id, video_id),
    CONSTRAINT video_likes_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT video_likes_video_id_fkey
        FOREIGN KEY (video_id) REFERENCES videos (id)
);

CREATE INDEX video_likes_video_id_idx ON video_likes (video_id);

CREATE TABLE video_favorites (
    user_id     BIGINT NOT NULL,
    video_id    BIGINT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT video_favorites_pkey PRIMARY KEY (user_id, video_id),
    CONSTRAINT video_favorites_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT video_favorites_video_id_fkey
        FOREIGN KEY (video_id) REFERENCES videos (id)
);

CREATE INDEX video_favorites_video_id_idx ON video_favorites (video_id);

CREATE TABLE user_follows (
    follower_user_id    BIGINT NOT NULL,
    followed_user_id    BIGINT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT user_follows_pkey PRIMARY KEY (follower_user_id, followed_user_id),
    CONSTRAINT user_follows_follower_user_id_fkey
        FOREIGN KEY (follower_user_id) REFERENCES users (id),
    CONSTRAINT user_follows_followed_user_id_fkey
        FOREIGN KEY (followed_user_id) REFERENCES users (id),
    CONSTRAINT user_follows_no_self_follow_check
        CHECK (follower_user_id <> followed_user_id)
);

CREATE INDEX user_follows_followed_user_id_idx ON user_follows (followed_user_id);

CREATE TABLE comments (
    id                  BIGSERIAL PRIMARY KEY,
    video_id            BIGINT NOT NULL,
    author_user_id      BIGINT NOT NULL,
    parent_comment_id   BIGINT,
    content             VARCHAR(2000) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT comments_video_id_fkey
        FOREIGN KEY (video_id) REFERENCES videos (id),
    CONSTRAINT comments_author_user_id_fkey
        FOREIGN KEY (author_user_id) REFERENCES users (id),
    CONSTRAINT comments_parent_comment_id_fkey
        FOREIGN KEY (parent_comment_id) REFERENCES comments (id),
    CONSTRAINT comments_status_check
        CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT comments_content_not_blank_check
        CHECK (char_length(btrim(content)) > 0)
);

CREATE INDEX comments_video_created_idx ON comments (video_id, created_at DESC);
CREATE INDEX comments_parent_created_idx ON comments (parent_comment_id, created_at);
