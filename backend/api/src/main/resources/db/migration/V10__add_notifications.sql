-- Durable per-user activity inbox. PostgreSQL is authoritative.
-- Rows are historical: unlike / unfollow / later deletes do not remove them.
CREATE TABLE notifications (
    id                  BIGSERIAL PRIMARY KEY,
    recipient_user_id   BIGINT NOT NULL,
    actor_user_id       BIGINT,
    type                VARCHAR(32) NOT NULL,
    video_id            BIGINT,
    comment_id          BIGINT,
    parent_comment_id   BIGINT,
    is_read             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at             TIMESTAMPTZ,
    CONSTRAINT notifications_recipient_user_id_fkey
        FOREIGN KEY (recipient_user_id) REFERENCES users (id),
    CONSTRAINT notifications_actor_user_id_fkey
        FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT notifications_video_id_fkey
        FOREIGN KEY (video_id) REFERENCES videos (id),
    CONSTRAINT notifications_comment_id_fkey
        FOREIGN KEY (comment_id) REFERENCES comments (id),
    CONSTRAINT notifications_parent_comment_id_fkey
        FOREIGN KEY (parent_comment_id) REFERENCES comments (id),
    CONSTRAINT notifications_type_check
        CHECK (type IN (
            'VIDEO_LIKED',
            'VIDEO_FAVORITED',
            'VIDEO_COMMENTED',
            'COMMENT_REPLIED',
            'USER_FOLLOWED'
        ))
);

-- Inbox pagination: newest first, then id for a deterministic tie-break.
CREATE INDEX notifications_recipient_created_idx
    ON notifications (recipient_user_id, created_at DESC, id DESC);

-- Unread-count lookups for the navigation badge.
CREATE INDEX notifications_recipient_unread_idx
    ON notifications (recipient_user_id, is_read);
