ALTER TABLE videos
    ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE videos
    ADD CONSTRAINT videos_view_count_check CHECK (view_count >= 0);

-- Newest-uploads feed: created_at DESC, id DESC.
CREATE INDEX videos_created_id_idx ON videos (created_at DESC, id DESC);

-- Retry-safe qualification: the same clientViewId cannot increment a video twice.
-- Rows are small (two identifiers + timestamp) and exist only for idempotency,
-- not as an analytics event stream.
CREATE TABLE video_view_idempotency (
    video_id        BIGINT NOT NULL,
    client_view_id  UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT video_view_idempotency_pkey PRIMARY KEY (video_id, client_view_id),
    CONSTRAINT video_view_idempotency_video_id_fkey
        FOREIGN KEY (video_id) REFERENCES videos (id)
);
