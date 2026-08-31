CREATE TABLE danmaku (
    id                  BIGSERIAL PRIMARY KEY,
    video_id            BIGINT NOT NULL,
    user_id             BIGINT NOT NULL,
    content             VARCHAR(200) NOT NULL,
    video_time_ms       BIGINT NOT NULL,
    style               VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    client_message_id   VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT danmaku_video_id_fkey
        FOREIGN KEY (video_id) REFERENCES videos (id),
    CONSTRAINT danmaku_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT danmaku_status_check
        CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT danmaku_style_check
        CHECK (style IN ('NORMAL', 'TOP', 'BOTTOM')),
    CONSTRAINT danmaku_content_not_blank_check
        CHECK (char_length(btrim(content)) > 0),
    CONSTRAINT danmaku_video_time_non_negative_check
        CHECK (video_time_ms >= 0),
    CONSTRAINT danmaku_user_client_message_uidx
        UNIQUE (user_id, client_message_id)
);

CREATE INDEX danmaku_video_time_id_idx
    ON danmaku (video_id, video_time_ms, id);

CREATE INDEX danmaku_video_created_idx
    ON danmaku (video_id, created_at);
