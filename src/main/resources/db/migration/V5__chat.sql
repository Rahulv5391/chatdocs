CREATE TABLE chat_session (
    id         UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id   UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL DEFAULT 'New chat',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_session_owner_updated ON chat_session (owner_id, updated_at DESC);

-- Optional scope (Phase 19): if a session has no rows here, chat uses all of the user's READY documents.
CREATE TABLE chat_session_document (
    session_id  UUID NOT NULL REFERENCES chat_session (id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    PRIMARY KEY (session_id, document_id)
);

CREATE TABLE chat_message (
    id         UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID        NOT NULL REFERENCES chat_session (id) ON DELETE CASCADE,
    -- Insertion order. Two messages saved in the same instant would tie on created_at.
    seq        BIGINT      GENERATED ALWAYS AS IDENTITY,
    role       VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content    TEXT        NOT NULL,
    citations  JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_message_session_seq ON chat_message (session_id, seq);
