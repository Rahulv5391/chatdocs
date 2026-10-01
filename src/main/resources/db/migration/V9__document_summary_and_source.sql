-- summary: short LLM-written overview, filled in at the end of ingestion (NULL if it couldn't be generated).
-- source_url: set when the document was imported from a web address instead of uploaded.
ALTER TABLE document ADD COLUMN summary TEXT;
ALTER TABLE document ADD COLUMN source_url VARCHAR(2000);
