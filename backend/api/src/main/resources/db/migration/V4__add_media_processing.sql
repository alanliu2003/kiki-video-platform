ALTER TABLE media_objects
    ADD COLUMN processing_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN processing_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN processing_error VARCHAR(500),
    ADD COLUMN processed_prefix VARCHAR(512),
    ADD COLUMN master_playlist_key VARCHAR(512),
    ADD COLUMN thumbnail_key VARCHAR(512),
    ADD COLUMN duration_seconds NUMERIC(12, 3),
    ADD COLUMN source_width INT,
    ADD COLUMN source_height INT,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN processed_at TIMESTAMPTZ;

ALTER TABLE media_objects
    ADD CONSTRAINT media_objects_processing_status_check
        CHECK (processing_status IN ('NOT_REQUESTED', 'PENDING', 'PROCESSING', 'READY', 'FAILED')),
    ADD CONSTRAINT media_objects_processing_attempts_check
        CHECK (processing_attempts >= 0);

CREATE INDEX media_objects_processing_status_idx
    ON media_objects (processing_status, updated_at);

CREATE TABLE media_processing_outbox (
    id                  BIGSERIAL PRIMARY KEY,
    media_object_id     BIGINT NOT NULL,
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
    CONSTRAINT media_processing_outbox_media_object_id_fkey
        FOREIGN KEY (media_object_id) REFERENCES media_objects (id),
    CONSTRAINT media_processing_outbox_status_check
        CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED')),
    CONSTRAINT media_processing_outbox_attempt_count_check
        CHECK (attempt_count >= 0)
);

CREATE UNIQUE INDEX media_processing_outbox_active_media_idx
    ON media_processing_outbox (media_object_id)
    WHERE status IN ('PENDING', 'PUBLISHING');

CREATE INDEX media_processing_outbox_due_idx
    ON media_processing_outbox (next_attempt_at, id)
    WHERE status IN ('PENDING', 'PUBLISHING');
