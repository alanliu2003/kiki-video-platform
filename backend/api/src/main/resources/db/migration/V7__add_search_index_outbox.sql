CREATE TABLE search_index_outbox (
    id                  BIGSERIAL PRIMARY KEY,
    video_id            BIGINT NOT NULL,
    event_type          VARCHAR(64) NOT NULL,
    event_version       INT NOT NULL,
    payload             TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL,
    attempt_count       INT NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error          VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,
    CONSTRAINT search_index_outbox_video_id_fkey
        FOREIGN KEY (video_id) REFERENCES videos (id),
    CONSTRAINT search_index_outbox_status_check
        CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED')),
    CONSTRAINT search_index_outbox_attempt_count_check
        CHECK (attempt_count >= 0)
);

CREATE UNIQUE INDEX search_index_outbox_active_upsert_idx
    ON search_index_outbox (video_id)
    WHERE status IN ('PENDING', 'PUBLISHING')
      AND event_type = 'VIDEO_SEARCH_UPSERT';

CREATE INDEX search_index_outbox_due_idx
    ON search_index_outbox (next_attempt_at, id)
    WHERE status IN ('PENDING', 'PUBLISHING');
