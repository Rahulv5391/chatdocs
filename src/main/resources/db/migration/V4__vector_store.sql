-- Same layout Spring AI's PgVectorStore would create itself; Flyway owns it instead (initialize-schema=false).
-- Chunks link to their document through metadata->>'document_id' (no foreign key: metadata is JSON).
CREATE TABLE vector_store (
    id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(768)
);

-- HNSW index for fast approximate nearest-neighbour search using cosine distance.
CREATE INDEX spring_ai_vector_index ON vector_store USING HNSW (embedding vector_cosine_ops);
