import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { EndpointError } from '@vaadin/hilla-frontend';
import { Button, ConfirmDialog, Grid, GridColumn, Notification } from '@vaadin/react-components';
import { ChatService } from 'Frontend/generated/endpoints';
import type ChatSessionDto from 'Frontend/generated/com/company/chatdocs/dto/ChatSessionDto';

export const config: ViewConfig = {
  title: 'Chat',
  menu: { order: 2 },
};

function showError(e: unknown, fallback: string) {
  const message = e instanceof EndpointError ? e.message : fallback;
  Notification.show(message, { theme: 'error', position: 'bottom-end', duration: 5000 });
}

export default function ChatIndexView() {
  const navigate = useNavigate();
  const [sessions, setSessions] = useState<ChatSessionDto[]>([]);
  const [toDelete, setToDelete] = useState<ChatSessionDto>();

  const refresh = useCallback(() => ChatService.listSessions().then(setSessions), []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  async function newChat() {
    try {
      const session = await ChatService.createSession();
      navigate(`/chat/${session.id}`);
    } catch (e) {
      showError(e, 'Could not create a chat.');
    }
  }

  async function confirmDelete() {
    if (!toDelete) return;
    const session = toDelete;
    setToDelete(undefined);
    try {
      await ChatService.deleteSession(session.id);
    } catch (e) {
      showError(e, 'Could not delete the chat.');
    } finally {
      await refresh();
    }
  }

  return (
    <main style={{ padding: '1rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <div>
        <Button theme="primary" onClick={newChat}>
          New chat
        </Button>
      </div>
      {sessions.length === 0 ? (
        <p>No chats yet. Start one with "New chat".</p>
      ) : (
        <Grid items={sessions} allRowsVisible={sessions.length < 20}>
          <GridColumn header="Chat" flexGrow={3}>
            {({ item }: { item: ChatSessionDto }) => (
              <a href={`/chat/${item.id}`} onClick={(e) => { e.preventDefault(); navigate(`/chat/${item.id}`); }}>
                {item.title}
              </a>
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
