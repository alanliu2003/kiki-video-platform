CREATE TABLE media_objects (
    id                  BIGSERIAL PRIMARY KEY,
    sha256              CHAR(64) NOT NULL,
    object_key          VARCHAR(512) NOT NULL,
    file_size_bytes     BIGINT NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT media_objects_sha256_key UNIQUE (sha256),
    CONSTRAINT media_objects_object_key_key UNIQUE (object_key),
    CONSTRAINT media_objects_sha256_format CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT media_objects_file_size_bytes_check CHECK (file_size_bytes > 0)
);

ALTER TABLE videos DROP CONSTRAINT videos_object_key_key;
ALTER TABLE videos ADD COLUMN media_object_id BIGINT;
ALTER TABLE videos ADD COLUMN file_sha256 CHAR(64);
ALTER TABLE videos ADD CONSTRAINT videos_media_object_id_fkey
    FOREIGN KEY (media_object_id) REFERENCES media_objects (id);
ALTER TABLE videos ADD CONSTRAINT videos_file_sha256_format
    CHECK (file_sha256 IS NULL OR file_sha256 ~ '^[0-9a-f]{64}$');

CREATE INDEX videos_media_object_id_idx ON videos (media_object_id);
CREATE INDEX videos_file_sha256_idx ON videos (file_sha256);

CREATE TABLE upload_sessions (
    id                  UUID PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    file_size_bytes     BIGINT NOT NULL,
    file_sha256         CHAR(64) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    chunk_size_bytes    BIGINT NOT NULL,
    total_chunks        INT NOT NULL,
    status              VARCHAR(20) NOT NULL,
    deduplicated        BOOLEAN NOT NULL DEFAULT FALSE,
    final_video_id      BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT upload_sessions_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT upload_sessions_final_video_id_fkey
        FOREIGN KEY (final_video_id) REFERENCES videos (id),
    CONSTRAINT upload_sessions_file_size_bytes_check CHECK (file_size_bytes > 0),
    CONSTRAINT upload_sessions_chunk_size_bytes_check CHECK (chunk_size_bytes > 0),
    CONSTRAINT upload_sessions_total_chunks_check CHECK (total_chunks > 0),
    CONSTRAINT upload_sessions_sha256_format CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT upload_sessions_status_check CHECK (status IN (
        'INITIATED', 'UPLOADING', 'COMPLETING', 'COMPLETED', 'FAILED', 'EXPIRED'
    ))
);

CREATE UNIQUE INDEX upload_sessions_active_user_sha256_idx
    ON upload_sessions (user_id, file_sha256)
    WHERE status IN ('INITIATED', 'UPLOADING', 'COMPLETING');

CREATE INDEX upload_sessions_expires_idx
    ON upload_sessions (expires_at)
    WHERE status IN ('INITIATED', 'UPLOADING', 'COMPLETING', 'FAILED');

CREATE TABLE upload_chunks (
    upload_session_id   UUID NOT NULL,
    chunk_index         INT NOT NULL,
    chunk_size_bytes    BIGINT NOT NULL,
    chunk_sha256        CHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (upload_session_id, chunk_index),
    CONSTRAINT upload_chunks_session_fkey
        FOREIGN KEY (upload_session_id) REFERENCES upload_sessions (id) ON DELETE CASCADE,
    CONSTRAINT upload_chunks_index_check CHECK (chunk_index >= 0),
    CONSTRAINT upload_chunks_size_check CHECK (chunk_size_bytes > 0),
    CONSTRAINT upload_chunks_sha256_format
        CHECK (chunk_sha256 IS NULL OR chunk_sha256 ~ '^[0-9a-f]{64}$')
);
