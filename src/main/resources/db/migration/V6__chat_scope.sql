-- true = the chat is limited to the documents in chat_session_document.
-- Needed because deleting a document removes its scope row: an emptied scope must search nothing, not everything.
ALTER TABLE chat_session ADD COLUMN scoped BOOLEAN NOT NULL DEFAULT FALSE;
