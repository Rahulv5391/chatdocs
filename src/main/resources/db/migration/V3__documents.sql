CREATE TABLE document (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id        UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    file_name       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    storage_path    VARCHAR(500) NOT NULL,
    checksum_sha256 VARCHAR(64)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED'
                    CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED')),
    chunk_count     INTEGER      NOT NULL DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (owner_id, checksum_sha256)
);

CREATE INDEX idx_document_owner_created ON document (owner_id, created_at DESC);
