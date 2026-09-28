import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { Button, ConfirmDialog, Dialog, Grid, GridColumn } from '@vaadin/react-components';
import { ChatService, DocumentService } from 'Frontend/generated/endpoints';
import DocumentStatus from 'Frontend/generated/com/company/chatdocs/entity/DocumentStatus';
import type ChatSessionDto from 'Frontend/generated/com/company/chatdocs/dto/ChatSessionDto';
import DocumentScopePicker from 'Frontend/components/DocumentScopePicker';
import { errorMessage, showError } from 'Frontend/util/notifications';

export const config: ViewConfig = {
  title: 'Chat',
  menu: { order: 2 },
};

export default function ChatIndexView() {
  const navigate = useNavigate();
  const [sessions, setSessions] = useState<ChatSessionDto[]>([]);
  const [toDelete, setToDelete] = useState<ChatSessionDto>();
  const [creating, setCreating] = useState(false);
  const [scope, setScope] = useState<string[]>([]);
  const [readyDocuments, setReadyDocuments] = useState<number>();

  const refresh = useCallback(() => ChatService.listSessions().then(setSessions), []);

  useEffect(() => {
    refresh();
    DocumentService.list().then((all) => setReadyDocuments(all.filter((d) => d.status === DocumentStatus.READY).length));
  }, [refresh]);

  async function createChat() {
    setCreating(false);
    try {
      const session = await ChatService.createSession(scope);
      navigate(`/chat/${session.id}`);
    } catch (e) {
      showError(errorMessage(e, 'Could not create a chat.'));
    }
  }

  async function confirmDelete() {
    if (!toDelete) return;
    const session = toDelete;
    setToDelete(undefined);
    try {
      await ChatService.deleteSession(session.id);
    } catch (e) {
      showError(errorMessage(e, 'Could not delete the chat.'));
    } finally {
      await refresh();
    }
  }

  return (
    <main style={{ padding: '1rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <div>
        <Button
          theme="primary"
          onClick={() => {
            setScope([]);
            setCreating(true);
          }}
        >
          New chat
        </Button>
      </div>
      {readyDocuments === 0 && (
        <p>
          You have no ready documents yet, so answers will be empty.{' '}
          <Link to="/documents">Upload documents first</Link>
          .
        </p>
      )}
      {sessions.length === 0 ? (
        <p>No chats yet. Start one with "New chat".</p>
      ) : (
        <Grid items={sessions} allRowsVisible={sessions.length < 20}>
          <GridColumn header="Chat" flexGrow={3}>
            {({ item }: { item: ChatSessionDto }) => (
              <Link to={`/chat/${item.id}`}>{item.title}</Link>
            )}
          </GridColumn>
          <GridColumn header="Last activity" autoWidth>
            {({ item }: { item: ChatSessionDto }) => new Date(item.updatedAt).toLocaleString()}
          </GridColumn>
          <GridColumn autoWidth flexGrow={0}>
            {({ item }: { item: ChatSessionDto }) => (
              <Button theme="error tertiary small" onClick={() => setToDelete(item)}>
                Delete
              </Button>
            )}
          </GridColumn>
        </Grid>
      )}
      <Dialog
        headerTitle="New chat"
        opened={creating}
        onOpenedChanged={(e) => setCreating(e.detail.value)}
        footer={
          <>
            <Button onClick={() => setCreating(false)}>Cancel</Button>
            <Button theme="primary" onClick={createChat}>
              Start chat
            </Button>
          </>
        }
      >
        <div style={{ width: 'min(28rem, 80vw)', minHeight: '10rem' }}>
          <p style={{ marginTop: 0 }}>Choose which documents this chat may answer from.</p>
          {creating && <DocumentScopePicker onChange={setScope} />}
        </div>
      </Dialog>
      <ConfirmDialog
        opened={!!toDelete}
        header="Delete chat?"
        cancelButtonVisible
        confirmText="Delete"
        confirmTheme="error primary"
        onConfirm={confirmDelete}
        onCancel={() => setToDelete(undefined)}
      >
        {toDelete && `"${toDelete.title}" and all its messages will be permanently deleted.`}
      </ConfirmDialog>
    </main>
  );
}
