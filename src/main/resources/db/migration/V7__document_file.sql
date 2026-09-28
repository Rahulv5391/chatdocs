-- The original uploaded files, kept in the database so they survive restarts on hosts without a persistent disk.
-- A separate table, so listing documents never loads file contents.
CREATE TABLE document_file (
    document_id UUID  PRIMARY KEY REFERENCES document (id) ON DELETE CASCADE,
    content     BYTEA NOT NULL
);
