CREATE TABLE videos (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT NOT NULL,
    title               VARCHAR(120) NOT NULL,
    description         VARCHAR(2000),
    object_key          VARCHAR(512) NOT NULL,
    original_filename   VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    file_size_bytes     BIGINT NOT NULL,
    status              VARCHAR(20) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT videos_owner_user_id_fkey
        FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT videos_object_key_key UNIQUE (object_key),
    CONSTRAINT videos_file_size_bytes_check CHECK (file_size_bytes >= 0),
    CONSTRAINT videos_status_check CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED'))
);

CREATE INDEX videos_owner_created_idx ON videos (owner_user_id, created_at DESC);
